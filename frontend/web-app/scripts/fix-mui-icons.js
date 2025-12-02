#!/usr/bin/env node

/**
 * FIX MUI ICON IMPORTS
 *
 * Converts barrel imports to individual imports for tree-shaking:
 *
 * Before (2.5 MB):
 * import { Settings, Dashboard } from '@mui/icons-material';
 *
 * After (tree-shakeable):
 * import SettingsIcon from '@mui/icons-material/Settings';
 * import DashboardIcon from '@mui/icons-material/Dashboard';
 *
 * Usage: node scripts/fix-mui-icons.js
 */

const fs = require('fs');
const path = require('path');
const glob = require('glob');

// ============================================================================
// CONFIGURATION
// ============================================================================
const SRC_DIR = path.join(__dirname, '../src');
const FILE_PATTERN = '**/*.{ts,tsx}';
const DRY_RUN = process.argv.includes('--dry-run');

// ============================================================================
// UTILITIES
// ============================================================================
function log(message, type = 'info') {
  const colors = {
    info: '\x1b[36m',
    success: '\x1b[32m',
    warning: '\x1b[33m',
    error: '\x1b[31m',
    reset: '\x1b[0m',
  };

  console.log(`${colors[type]}${message}${colors.reset}`);
}

function capitalizeWords(str) {
  return str
    .split(/(?=[A-Z])/)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join('');
}

// ============================================================================
// ICON IMPORT FIXER
// ============================================================================
function fixIconImports(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  let modified = content;
  let changes = 0;

  // Regex to find MUI icon barrel imports
  const importRegex = /import\s+\{([^}]+)\}\s+from\s+['"]@mui\/icons-material['"]/g;

  let match;
  while ((match = importRegex.exec(content)) !== null) {
    const importStatement = match[0];
    const icons = match[1]
      .split(',')
      .map(icon => icon.trim())
      .filter(icon => icon.length > 0);

    // Generate individual imports
    const individualImports = icons.map(iconName => {
      // Handle 'as' aliases
      const parts = iconName.split(/\s+as\s+/);
      const importName = parts[0].trim();
      const alias = parts[1]?.trim() || importName;

      // Ensure icon name ends with 'Icon' for consistency
      const variableName = alias.endsWith('Icon') ? alias : `${alias}Icon`;

      return `import ${variableName} from '@mui/icons-material/${importName}';`;
    }).join('\n');

    // Replace the barrel import
    modified = modified.replace(importStatement, individualImports);
    changes++;
  }

  return { modified, changes };
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================
function main() {
  log('🔍 Scanning for MUI icon imports...', 'info');

  const files = glob.sync(FILE_PATTERN, {
    cwd: SRC_DIR,
    absolute: true,
    ignore: ['**/node_modules/**', '**/dist/**', '**/*.d.ts'],
  });

  log(`📄 Found ${files.length} files to check\n`, 'info');

  let totalChanges = 0;
  let filesChanged = 0;

  files.forEach(file => {
    const relativePath = path.relative(process.cwd(), file);
    const { modified, changes } = fixIconImports(file);

    if (changes > 0) {
      filesChanged++;
      totalChanges += changes;

      log(`✓ ${relativePath} (${changes} import(s))`, 'success');

      if (!DRY_RUN) {
        fs.writeFileSync(file, modified, 'utf8');
      }
    }
  });

  log('', 'info');
  log('━'.repeat(60), 'info');
  log(`📊 Summary:`, 'info');
  log(`   Files scanned: ${files.length}`, 'info');
  log(`   Files changed: ${filesChanged}`, 'success');
  log(`   Total imports fixed: ${totalChanges}`, 'success');

  if (DRY_RUN) {
    log(`   Mode: DRY RUN (no files modified)`, 'warning');
    log(`   Run without --dry-run to apply changes`, 'warning');
  } else {
    log(`   Mode: LIVE (files modified)`, 'success');
  }

  log('━'.repeat(60), 'info');

  if (filesChanged > 0) {
    log('', 'info');
    log('💡 Expected impact:', 'info');
    log('   Bundle size reduction: ~2 MB', 'success');
    log('   Tree-shaking: Enabled', 'success');
    log('   Only imported icons will be bundled', 'success');
  }
}

// ============================================================================
// RUN
// ============================================================================
try {
  main();
} catch (error) {
  log(`❌ Error: ${error.message}`, 'error');
  process.exit(1);
}
