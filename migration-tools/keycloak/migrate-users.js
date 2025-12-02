#!/usr/bin/env node

/**
 * User Migration Script from Legacy JWT to Keycloak
 * This script migrates existing users from the application database to Keycloak
 */

const axios = require('axios');
const { Pool } = require('pg');
const readline = require('readline');

// Configuration
const config = {
  // Database configuration
  database: {
    host: process.env.DB_HOST || 'localhost',
    port: process.env.DB_PORT || 5432,
    database: process.env.DB_NAME || 'waqiti',
    user: process.env.DB_USER || 'app_user',
    password: process.env.DB_PASSWORD || 'password',
  },
  // Keycloak configuration
  keycloak: {
    baseUrl: process.env.KEYCLOAK_URL || 'http://localhost:8180',
    realm: process.env.KEYCLOAK_REALM || 'waqiti-fintech',
    adminUsername: process.env.KEYCLOAK_ADMIN || 'admin',
    adminPassword: process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin',
    clientId: 'admin-cli',
  },
  // Migration options
  migration: {
    batchSize: parseInt(process.env.BATCH_SIZE) || 100,
    dryRun: process.env.DRY_RUN === 'true',
    skipExisting: process.env.SKIP_EXISTING !== 'false',
    setTemporaryPassword: process.env.SET_TEMP_PASSWORD === 'true',
    defaultPassword: process.env.DEFAULT_PASSWORD || 'ChangeMePlease123!',
  }
};

// Database connection
const pool = new Pool(config.database);

// Keycloak admin client
class KeycloakAdmin {
  constructor() {
    this.baseUrl = config.keycloak.baseUrl;
    this.realm = config.keycloak.realm;
    this.accessToken = null;
  }

  async authenticate() {
    try {
      const response = await axios.post(
        `${this.baseUrl}/realms/master/protocol/openid-connect/token`,
        new URLSearchParams({
          client_id: config.keycloak.clientId,
          username: config.keycloak.adminUsername,
          password: config.keycloak.adminPassword,
          grant_type: 'password',
        }),
        {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        }
      );
      
      this.accessToken = response.data.access_token;
      console.log('✓ Authenticated with Keycloak');
      return true;
    } catch (error) {
      console.error('✗ Failed to authenticate with Keycloak:', error.message);
      return false;
    }
  }

  async createUser(userData) {
    if (!this.accessToken) {
      throw new Error('Not authenticated with Keycloak');
    }

    try {
      const response = await axios.post(
        `${this.baseUrl}/admin/realms/${this.realm}/users`,
        userData,
        {
          headers: {
            'Authorization': `Bearer ${this.accessToken}`,
            'Content-Type': 'application/json',
          },
        }
      );
      
      // Extract user ID from location header
      const location = response.headers.location;
      const userId = location ? location.split('/').pop() : null;
      
      return { success: true, userId };
    } catch (error) {
      if (error.response?.status === 409) {
        return { success: false, error: 'User already exists' };
      }
      throw error;
    }
  }

  async setUserPassword(userId, password, temporary = false) {
    if (!this.accessToken) {
      throw new Error('Not authenticated with Keycloak');
    }

    try {
      await axios.put(
        `${this.baseUrl}/admin/realms/${this.realm}/users/${userId}/reset-password`,
        {
          type: 'password',
          value: password,
          temporary: temporary,
        },
        {
          headers: {
            'Authorization': `Bearer ${this.accessToken}`,
            'Content-Type': 'application/json',
          },
        }
      );
      return true;
    } catch (error) {
      console.error(`Failed to set password for user ${userId}:`, error.message);
      return false;
    }
  }

  async assignRoles(userId, roles) {
    if (!this.accessToken) {
      throw new Error('Not authenticated with Keycloak');
    }

    try {
      // Get available realm roles
      const rolesResponse = await axios.get(
        `${this.baseUrl}/admin/realms/${this.realm}/roles`,
        {
          headers: {
            'Authorization': `Bearer ${this.accessToken}`,
          },
        }
      );

      const availableRoles = rolesResponse.data;
      const rolesToAssign = [];

      for (const roleName of roles) {
        const role = availableRoles.find(r => r.name === roleName);
        if (role) {
          rolesToAssign.push(role);
        } else {
          console.warn(`  ⚠ Role '${roleName}' not found in realm`);
        }
      }

      if (rolesToAssign.length > 0) {
        await axios.post(
          `${this.baseUrl}/admin/realms/${this.realm}/users/${userId}/role-mappings/realm`,
          rolesToAssign,
          {
            headers: {
              'Authorization': `Bearer ${this.accessToken}`,
              'Content-Type': 'application/json',
            },
          }
        );
      }

      return true;
    } catch (error) {
      console.error(`Failed to assign roles to user ${userId}:`, error.message);
      return false;
    }
  }

  async getUserByUsername(username) {
    if (!this.accessToken) {
      throw new Error('Not authenticated with Keycloak');
    }

    try {
      const response = await axios.get(
        `${this.baseUrl}/admin/realms/${this.realm}/users`,
        {
          params: { username: username, exact: true },
          headers: {
            'Authorization': `Bearer ${this.accessToken}`,
          },
        }
      );
      
      return response.data.length > 0 ? response.data[0] : null;
    } catch (error) {
      console.error(`Failed to get user ${username}:`, error.message);
      return null;
    }
  }
}

// Migration functions
async function fetchUsers(offset, limit) {
  const query = `
    SELECT 
      u.id,
      u.username,
      u.email,
      u.phone_number,
      u.first_name,
      u.last_name,
      u.status,
      u.email_verified,
      u.created_at,
      u.keycloak_id,
      array_agg(r.role) as roles
    FROM users u
    LEFT JOIN user_roles r ON u.id = r.user_id
    GROUP BY u.id
    ORDER BY u.created_at
    LIMIT $1 OFFSET $2
  `;
  
  const result = await pool.query(query, [limit, offset]);
  return result.rows;
}

async function countUsers() {
  const result = await pool.query('SELECT COUNT(*) FROM users');
  return parseInt(result.rows[0].count);
}

async function updateUserKeycloakId(userId, keycloakId) {
  const query = 'UPDATE users SET keycloak_id = $1 WHERE id = $2';
  await pool.query(query, [keycloakId, userId]);
}

function mapUserToKeycloak(user) {
  return {
    username: user.username,
    email: user.email,
    emailVerified: user.email_verified || false,
    enabled: user.status === 'ACTIVE',
    firstName: user.first_name,
    lastName: user.last_name,
    attributes: {
      phoneNumber: user.phone_number ? [user.phone_number] : [],
      legacyUserId: [user.id],
      migrationDate: [new Date().toISOString()],
      createdAt: [user.created_at.toISOString()],
    },
  };
}

function mapRoles(roles) {
  if (!roles || roles[0] === null) return ['user'];
  
  // Map database roles to Keycloak roles
  const roleMapping = {
    'USER': 'user',
    'PREMIUM_USER': 'premium_user',
    'MERCHANT': 'merchant',
    'ADMIN': 'admin',
    'SUPER_ADMIN': 'super_admin',
    'SUPPORT': 'support_agent',
    'COMPLIANCE': 'compliance_officer',
  };
  
  return roles.map(role => roleMapping[role] || role.toLowerCase());
}

// Progress tracking
class ProgressTracker {
  constructor(total) {
    this.total = total;
    this.processed = 0;
    this.succeeded = 0;
    this.failed = 0;
    this.skipped = 0;
    this.startTime = Date.now();
  }

  update(result) {
    this.processed++;
    if (result === 'success') this.succeeded++;
    else if (result === 'failed') this.failed++;
    else if (result === 'skipped') this.skipped++;
  }

  getProgress() {
    const elapsed = (Date.now() - this.startTime) / 1000;
    const rate = this.processed / elapsed;
    const remaining = (this.total - this.processed) / rate;
    
    return {
      percentage: ((this.processed / this.total) * 100).toFixed(2),
      rate: rate.toFixed(2),
      elapsed: this.formatTime(elapsed),
      remaining: this.formatTime(remaining),
    };
  }

  formatTime(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    return `${h}h ${m}m ${s}s`;
  }

  printSummary() {
    console.log('\n' + '='.repeat(50));
    console.log('Migration Summary:');
    console.log('='.repeat(50));
    console.log(`Total users:     ${this.total}`);
    console.log(`Processed:       ${this.processed}`);
    console.log(`Succeeded:       ${this.succeeded}`);
    console.log(`Failed:          ${this.failed}`);
    console.log(`Skipped:         ${this.skipped}`);
    console.log(`Success rate:    ${((this.succeeded / this.processed) * 100).toFixed(2)}%`);
    console.log(`Total time:      ${this.formatTime((Date.now() - this.startTime) / 1000)}`);
  }
}

// Main migration function
async function migrateUsers() {
  console.log('Starting user migration to Keycloak...\n');
  
  // Initialize Keycloak admin client
  const keycloakAdmin = new KeycloakAdmin();
  const authenticated = await keycloakAdmin.authenticate();
  
  if (!authenticated) {
    console.error('Failed to authenticate with Keycloak. Exiting.');
    process.exit(1);
  }
  
  // Get total user count
  const totalUsers = await countUsers();
  console.log(`Found ${totalUsers} users to migrate\n`);
  
  if (config.migration.dryRun) {
    console.log('🔸 DRY RUN MODE - No changes will be made\n');
  }
  
  // Initialize progress tracker
  const tracker = new ProgressTracker(totalUsers);
  
  // Process users in batches
  let offset = 0;
  const batchSize = config.migration.batchSize;
  
  while (offset < totalUsers) {
    const users = await fetchUsers(offset, batchSize);
    
    console.log(`\nProcessing batch ${Math.floor(offset / batchSize) + 1} (${users.length} users)`);
    console.log('-'.repeat(40));
    
    for (const user of users) {
      process.stdout.write(`  ${user.username}... `);
      
      try {
        // Check if user already has Keycloak ID
        if (user.keycloak_id && config.migration.skipExisting) {
          console.log('✓ Already migrated');
          tracker.update('skipped');
          continue;
        }
        
        // Check if user exists in Keycloak
        const existingUser = await keycloakAdmin.getUserByUsername(user.username);
        if (existingUser && config.migration.skipExisting) {
          console.log('✓ Already exists in Keycloak');
          
          // Update local database with Keycloak ID
          if (!config.migration.dryRun) {
            await updateUserKeycloakId(user.id, existingUser.id);
          }
          
          tracker.update('skipped');
          continue;
        }
        
        // Prepare user data
        const keycloakUser = mapUserToKeycloak(user);
        const roles = mapRoles(user.roles);
        
        if (config.migration.dryRun) {
          console.log('✓ Would migrate (dry run)');
          tracker.update('success');
        } else {
          // Create user in Keycloak
          const result = await keycloakAdmin.createUser(keycloakUser);
          
          if (result.success) {
            // Set password
            if (config.migration.setTemporaryPassword) {
              await keycloakAdmin.setUserPassword(
                result.userId,
                config.migration.defaultPassword,
                true
              );
            }
            
            // Assign roles
            await keycloakAdmin.assignRoles(result.userId, roles);
            
            // Update local database with Keycloak ID
            await updateUserKeycloakId(user.id, result.userId);
            
            console.log('✓ Migrated successfully');
            tracker.update('success');
          } else {
            console.log(`✗ ${result.error}`);
            tracker.update('failed');
          }
        }
      } catch (error) {
        console.log(`✗ Error: ${error.message}`);
        tracker.update('failed');
      }
    }
    
    // Print progress
    const progress = tracker.getProgress();
    console.log(`\nProgress: ${progress.percentage}% | Rate: ${progress.rate} users/sec`);
    console.log(`Time: ${progress.elapsed} elapsed | ${progress.remaining} remaining`);
    
    offset += batchSize;
    
    // Re-authenticate if needed (token might expire for large migrations)
    if (offset % (batchSize * 10) === 0) {
      await keycloakAdmin.authenticate();
    }
  }
  
  // Print final summary
  tracker.printSummary();
}

// CLI interface
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
});

async function main() {
  console.log('Waqiti User Migration to Keycloak');
  console.log('==================================\n');
  
  // Show configuration
  console.log('Configuration:');
  console.log(`  Database: ${config.database.host}:${config.database.port}/${config.database.database}`);
  console.log(`  Keycloak: ${config.keycloak.baseUrl}/realms/${config.keycloak.realm}`);
  console.log(`  Batch size: ${config.migration.batchSize}`);
  console.log(`  Dry run: ${config.migration.dryRun}`);
  console.log(`  Skip existing: ${config.migration.skipExisting}`);
  console.log(`  Set temporary password: ${config.migration.setTemporaryPassword}\n`);
  
  // Confirm before proceeding
  if (!config.migration.dryRun) {
    rl.question('This will migrate users to Keycloak. Continue? (yes/no): ', async (answer) => {
      if (answer.toLowerCase() === 'yes') {
        try {
          await migrateUsers();
        } catch (error) {
          console.error('\nMigration failed:', error.message);
          process.exit(1);
        } finally {
          await pool.end();
          rl.close();
        }
      } else {
        console.log('Migration cancelled.');
        rl.close();
        process.exit(0);
      }
    });
  } else {
    try {
      await migrateUsers();
    } catch (error) {
      console.error('\nMigration failed:', error.message);
      process.exit(1);
    } finally {
      await pool.end();
      rl.close();
    }
  }
}

// Run migration
main().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});