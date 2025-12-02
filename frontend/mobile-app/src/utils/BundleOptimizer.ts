/**
 * Bundle Optimizer - Advanced bundle analysis and optimization utilities
 * Analyzes bundle size, identifies optimization opportunities, and provides bundle splitting strategies
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { DeviceEventEmitter } from 'react-native';
import PerformanceMonitor from './PerformanceMonitor';

export interface BundleAnalysis {
  totalSize: number;
  compressedSize: number;
  compressionRatio: number;
  modules: ModuleInfo[];
  chunks: ChunkInfo[];
  duplicates: DuplicateInfo[];
  largeDependencies: LargeDependencyInfo[];
  unusedExports: UnusedExportInfo[];
  treeShakingOpportunities: TreeShakingInfo[];
  recommendations: OptimizationRecommendation[];
}

export interface ModuleInfo {
  id: string;
  name: string;
  path: string;
  size: number;
  compressedSize: number;
  chunks: string[];
  dependencies: string[];
  exports: string[];
  imports: ImportInfo[];
  isEntry: boolean;
  isVendor: boolean;
  loadTime?: number;
}

export interface ChunkInfo {
  id: string;
  name: string;
  size: number;
  compressedSize: number;
  modules: string[];
  entrypoint: boolean;
  loadTime?: number;
  parents: string[];
  children: string[];
}

export interface ImportInfo {
  module: string;
  exports: string[];
  isUsed: boolean;
  type: 'static' | 'dynamic' | 'require';
}

export interface DuplicateInfo {
  module: string;
  chunks: string[];
  totalSize: number;
  potentialSavings: number;
}

export interface LargeDependencyInfo {
  name: string;
  size: number;
  usage: number; // Percentage of code actually used
  alternatives: string[];
  recommendation: string;
}

export interface UnusedExportInfo {
  module: string;
  exports: string[];
  potentialSavings: number;
}

export interface TreeShakingInfo {
  module: string;
  unusedCode: number; // Bytes
  potentialSavings: number;
  difficulty: 'easy' | 'medium' | 'hard';
}

export interface OptimizationRecommendation {
  type: 'duplicate' | 'tree-shaking' | 'code-splitting' | 'lazy-loading' | 'dependency' | 'compression';
  priority: 'low' | 'medium' | 'high' | 'critical';
  description: string;
  potentialSavings: number; // Bytes
  effort: 'low' | 'medium' | 'high';
  impact: 'low' | 'medium' | 'high';
  implementation: string;
}

export interface BundleMetrics {
  size: {
    total: number;
    compressed: number;
    mainBundle: number;
    vendors: number;
    chunks: number;
  };
  performance: {
    loadTime: number;
    parseTime: number;
    compressionRatio: number;
  };
  optimization: {
    duplicateCode: number;
    unusedCode: number;
    treeShakingOpportunities: number;
  };
  timestamp: number;
}

class BundleOptimizer {
  private static instance: BundleOptimizer;
  private bundleMetrics: BundleMetrics[] = [];
  private analysisCache: Map<string, BundleAnalysis> = new Map();
  private trackingEnabled: boolean = false;
  
  // Size thresholds (bytes)
  private readonly THRESHOLDS = {
    LARGE_MODULE: 100 * 1024, // 100KB
    HUGE_MODULE: 500 * 1024, // 500KB
    LARGE_CHUNK: 250 * 1024, // 250KB
    DUPLICATE_THRESHOLD: 10 * 1024, // 10KB
    UNUSED_CODE_THRESHOLD: 5 * 1024, // 5KB
  };

  private constructor() {
    this.loadBundleHistory();
  }

  static getInstance(): BundleOptimizer {
    if (!BundleOptimizer.instance) {
      BundleOptimizer.instance = new BundleOptimizer();
    }
    return BundleOptimizer.instance;
  }

  /**
   * Start bundle tracking
   */
  startTracking(): void {
    this.trackingEnabled = true;
    this.instrumentBundleLoading();
  }

  /**
   * Stop bundle tracking
   */
  stopTracking(): void {
    this.trackingEnabled = false;
  }

  /**
   * Instrument bundle loading for metrics collection
   */
  private instrumentBundleLoading(): void {
    // Monitor require calls (Metro bundler)
    if (typeof require !== 'undefined' && require.importAll) {
      const originalImportAll = require.importAll;
      
      require.importAll = function(this: any, ...args: any[]) {
        const startTime = performance.now();
        
        try {
          const result = originalImportAll.apply(this, args);
          const loadTime = performance.now() - startTime;
          
          // Record bundle load time
          BundleOptimizer.getInstance().recordBundleLoad('importAll', loadTime, args);
          
          return result;
        } catch (error) {
          console.error('Bundle import error:', error);
          throw error;
        }
      };
    }

    // Monitor dynamic imports
    this.instrumentDynamicImports();
  }

  /**
   * Instrument dynamic imports
   */
  private instrumentDynamicImports(): void {
    // Override global import function if available
    if (typeof globalThis !== 'undefined' && globalThis.importScripts) {
      const originalImport = globalThis.importScripts;
      
      globalThis.importScripts = function(...args: any[]) {
        const startTime = performance.now();
        
        try {
          const result = originalImport.apply(this, args);
          const loadTime = performance.now() - startTime;
          
          BundleOptimizer.getInstance().recordBundleLoad('dynamic_import', loadTime, args);
          
          return result;
        } catch (error) {
          console.error('Dynamic import error:', error);
          throw error;
        }
      };
    }
  }

  /**
   * Record bundle load metrics
   */
  recordBundleLoad(type: string, loadTime: number, args: any[]): void {
    if (!this.trackingEnabled) return;

    PerformanceMonitor.recordMetric({
      name: `bundle_load_${type}`,
      value: loadTime,
      timestamp: Date.now(),
      category: 'bundle',
      metadata: {
        type,
        args: args.map(String),
      },
    });
  }

  /**
   * Analyze current bundle
   */
  async analyzeBundleStructure(): Promise<BundleAnalysis> {
    const cacheKey = `bundle_analysis_${Date.now()}`;
    
    if (this.analysisCache.has(cacheKey)) {
      return this.analysisCache.get(cacheKey)!;
    }

    try {
      const analysis = await this.performBundleAnalysis();
      this.analysisCache.set(cacheKey, analysis);
      
      // Cleanup old cache entries
      if (this.analysisCache.size > 10) {
        const firstKey = this.analysisCache.keys().next().value;
        this.analysisCache.delete(firstKey);
      }
      
      return analysis;
    } catch (error) {
      console.error('Bundle analysis failed:', error);
      throw error;
    }
  }

  /**
   * Perform comprehensive bundle analysis
   */
  private async performBundleAnalysis(): Promise<BundleAnalysis> {
    // This is a simplified implementation
    // In a real scenario, you'd need access to Metro bundler output or webpack stats
    
    const modules = await this.analyzeModules();
    const chunks = await this.analyzeChunks();
    const duplicates = this.findDuplicates(modules);
    const largeDependencies = this.identifyLargeDependencies(modules);
    const unusedExports = await this.findUnusedExports(modules);
    const treeShakingOpportunities = this.identifyTreeShakingOpportunities(modules);
    
    const totalSize = modules.reduce((sum, module) => sum + module.size, 0);
    const compressedSize = modules.reduce((sum, module) => sum + module.compressedSize, 0);
    
    const recommendations = this.generateRecommendations(
      modules,
      duplicates,
      largeDependencies,
      unusedExports,
      treeShakingOpportunities
    );

    return {
      totalSize,
      compressedSize,
      compressionRatio: compressedSize / totalSize,
      modules,
      chunks,
      duplicates,
      largeDependencies,
      unusedExports,
      treeShakingOpportunities,
      recommendations,
    };
  }

  /**
   * Analyze loaded modules
   */
  private async analyzeModules(): Promise<ModuleInfo[]> {
    const modules: ModuleInfo[] = [];
    
    // Analyze React Native modules
    if (typeof require !== 'undefined' && require.getModules) {
      const moduleMap = require.getModules();
      
      for (const [id, moduleFactory] of Object.entries(moduleMap)) {
        if (typeof moduleFactory === 'function') {
          const moduleInfo = await this.analyzeModule(id, moduleFactory);
          modules.push(moduleInfo);
        }
      }
    }
    
    return modules;
  }

  /**
   * Analyze individual module
   */
  private async analyzeModule(id: string, moduleFactory: Function): Promise<ModuleInfo> {
    const code = moduleFactory.toString();
    const size = code.length * 2; // Rough byte estimate
    const compressedSize = Math.floor(size * 0.7); // Estimated compression
    
    // Extract basic information from code
    const dependencies = this.extractDependencies(code);
    const exports = this.extractExports(code);
    const imports = this.extractImports(code);
    
    // Determine if module is vendor code
    const isVendor = this.isVendorModule(code, id);
    
    return {
      id,
      name: this.getModuleName(id, code),
      path: this.getModulePath(id),
      size,
      compressedSize,
      chunks: [id], // Simplified
      dependencies,
      exports,
      imports,
      isEntry: id === '0', // Main entry is usually 0 in Metro
      isVendor,
    };
  }

  /**
   * Analyze chunks
   */
  private async analyzeChunks(): Promise<ChunkInfo[]> {
    // Simplified chunk analysis
    // In reality, this would analyze Metro's bundle chunks
    return [
      {
        id: 'main',
        name: 'main.bundle.js',
        size: 500 * 1024, // 500KB estimated
        compressedSize: 150 * 1024, // 150KB compressed
        modules: ['0', '1', '2'], // Main modules
        entrypoint: true,
        parents: [],
        children: [],
      },
    ];
  }

  /**
   * Find duplicate code across chunks
   */
  private findDuplicates(modules: ModuleInfo[]): DuplicateInfo[] {
    const duplicates: DuplicateInfo[] = [];
    const modulesByName: Map<string, ModuleInfo[]> = new Map();
    
    // Group modules by name
    modules.forEach(module => {
      const name = module.name;
      if (!modulesByName.has(name)) {
        modulesByName.set(name, []);
      }
      modulesByName.get(name)!.push(module);
    });

    // Find duplicates
    modulesByName.forEach((moduleList, name) => {
      if (moduleList.length > 1) {
        const totalSize = moduleList.reduce((sum, module) => sum + module.size, 0);
        const potentialSavings = totalSize - moduleList[0].size; // Keep one copy
        
        if (potentialSavings > this.THRESHOLDS.DUPLICATE_THRESHOLD) {
          duplicates.push({
            module: name,
            chunks: moduleList.map(m => m.chunks[0]),
            totalSize,
            potentialSavings,
          });
        }
      }
    });

    return duplicates;
  }

  /**
   * Identify large dependencies
   */
  private identifyLargeDependencies(modules: ModuleInfo[]): LargeDependencyInfo[] {
    const largeDeps: LargeDependencyInfo[] = [];
    
    modules
      .filter(module => module.isVendor && module.size > this.THRESHOLDS.LARGE_MODULE)
      .forEach(module => {
        const usage = this.estimateUsage(module);
        const alternatives = this.suggestAlternatives(module.name);
        
        largeDeps.push({
          name: module.name,
          size: module.size,
          usage,
          alternatives,
          recommendation: this.createUsageRecommendation(module.name, usage),
        });
      });

    return largeDeps;
  }

  /**
   * Find unused exports
   */
  private async findUnusedExports(modules: ModuleInfo[]): Promise<UnusedExportInfo[]> {
    const unusedExports: UnusedExportInfo[] = [];
    const allImports = new Set<string>();
    
    // Collect all imports
    modules.forEach(module => {
      module.imports.forEach(imp => {
        imp.exports.forEach(exp => {
          allImports.add(`${imp.module}:${exp}`);
        });
      });
    });

    // Find unused exports
    modules.forEach(module => {
      const unused = module.exports.filter(exp => 
        !allImports.has(`${module.name}:${exp}`)
      );

      if (unused.length > 0) {
        const potentialSavings = Math.floor(module.size * (unused.length / module.exports.length));
        
        if (potentialSavings > this.THRESHOLDS.UNUSED_CODE_THRESHOLD) {
          unusedExports.push({
            module: module.name,
            exports: unused,
            potentialSavings,
          });
        }
      }
    });

    return unusedExports;
  }

  /**
   * Identify tree shaking opportunities
   */
  private identifyTreeShakingOpportunities(modules: ModuleInfo[]): TreeShakingInfo[] {
    const opportunities: TreeShakingInfo[] = [];
    
    modules
      .filter(module => module.isVendor)
      .forEach(module => {
        const unusedCode = this.estimateUnusedCode(module);
        const potentialSavings = Math.floor(module.size * (unusedCode / 100));
        
        if (potentialSavings > this.THRESHOLDS.UNUSED_CODE_THRESHOLD) {
          opportunities.push({
            module: module.name,
            unusedCode: potentialSavings,
            potentialSavings,
            difficulty: this.assessTreeShakingDifficulty(module),
          });
        }
      });

    return opportunities;
  }

  /**
   * Generate optimization recommendations
   */
  private generateRecommendations(
    modules: ModuleInfo[],
    duplicates: DuplicateInfo[],
    largeDeps: LargeDependencyInfo[],
    unusedExports: UnusedExportInfo[],
    treeShaking: TreeShakingInfo[]
  ): OptimizationRecommendation[] {
    const recommendations: OptimizationRecommendation[] = [];

    // Duplicate code recommendations
    duplicates.forEach(duplicate => {
      recommendations.push({
        type: 'duplicate',
        priority: duplicate.potentialSavings > 50 * 1024 ? 'high' : 'medium',
        description: `Remove duplicate module: ${duplicate.module}`,
        potentialSavings: duplicate.potentialSavings,
        effort: 'medium',
        impact: duplicate.potentialSavings > 100 * 1024 ? 'high' : 'medium',
        implementation: 'Extract common module to shared chunk or optimize imports',
      });
    });

    // Large dependency recommendations
    largeDeps
      .filter(dep => dep.usage < 50)
      .forEach(dep => {
        recommendations.push({
          type: 'dependency',
          priority: 'high',
          description: `Replace or optimize large dependency: ${dep.name}`,
          potentialSavings: Math.floor(dep.size * 0.7),
          effort: 'high',
          impact: 'high',
          implementation: dep.alternatives.length > 0 
            ? `Consider alternatives: ${dep.alternatives.join(', ')}`
            : 'Look for lighter alternatives or implement custom solution',
        });
      });

    // Tree shaking recommendations
    treeShaking
      .filter(ts => ts.difficulty !== 'hard')
      .forEach(ts => {
        recommendations.push({
          type: 'tree-shaking',
          priority: ts.potentialSavings > 50 * 1024 ? 'high' : 'medium',
          description: `Enable tree shaking for ${ts.module}`,
          potentialSavings: ts.potentialSavings,
          effort: ts.difficulty === 'easy' ? 'low' : 'medium',
          impact: 'medium',
          implementation: 'Use named imports and ensure side-effect-free modules',
        });
      });

    // Code splitting recommendations
    const largeModules = modules.filter(m => 
      m.size > this.THRESHOLDS.LARGE_MODULE && !m.isEntry
    );
    
    if (largeModules.length > 0) {
      recommendations.push({
        type: 'code-splitting',
        priority: 'medium',
        description: `Split ${largeModules.length} large modules into separate chunks`,
        potentialSavings: largeModules.reduce((sum, m) => sum + m.size, 0) * 0.3,
        effort: 'medium',
        impact: 'high',
        implementation: 'Use dynamic imports for route-based or feature-based splitting',
      });
    }

    // Lazy loading recommendations
    const nonCriticalModules = modules.filter(m => 
      !m.isEntry && !this.isCriticalPath(m)
    );
    
    if (nonCriticalModules.length > 3) {
      recommendations.push({
        type: 'lazy-loading',
        priority: 'medium',
        description: 'Implement lazy loading for non-critical components',
        potentialSavings: nonCriticalModules.reduce((sum, m) => sum + m.size, 0) * 0.5,
        effort: 'medium',
        impact: 'high',
        implementation: 'Use React.lazy() or dynamic imports for components not needed at startup',
      });
    }

    return recommendations.sort((a, b) => {
      const priorityOrder = { critical: 4, high: 3, medium: 2, low: 1 };
      return priorityOrder[b.priority] - priorityOrder[a.priority];
    });
  }

  /**
   * Record bundle metrics
   */
  recordBundleMetrics(metrics: Partial<BundleMetrics>): void {
    const fullMetrics: BundleMetrics = {
      size: {
        total: 0,
        compressed: 0,
        mainBundle: 0,
        vendors: 0,
        chunks: 0,
        ...metrics.size,
      },
      performance: {
        loadTime: 0,
        parseTime: 0,
        compressionRatio: 0,
        ...metrics.performance,
      },
      optimization: {
        duplicateCode: 0,
        unusedCode: 0,
        treeShakingOpportunities: 0,
        ...metrics.optimization,
      },
      timestamp: Date.now(),
    };

    this.bundleMetrics.push(fullMetrics);
    
    // Keep only last 50 metrics
    if (this.bundleMetrics.length > 50) {
      this.bundleMetrics.shift();
    }

    // Store in AsyncStorage
    this.storeBundleHistory();

    // Emit event
    DeviceEventEmitter.emit('bundleMetrics', fullMetrics);
  }

  /**
   * Get bundle size trends
   */
  getBundleTrends(periodDays: number = 7): {
    dates: string[];
    totalSize: number[];
    compressionRatio: number[];
    loadTime: number[];
  } {
    const cutoff = Date.now() - (periodDays * 24 * 60 * 60 * 1000);
    const recentMetrics = this.bundleMetrics.filter(m => m.timestamp > cutoff);

    return {
      dates: recentMetrics.map(m => new Date(m.timestamp).toLocaleDateString()),
      totalSize: recentMetrics.map(m => m.size.total),
      compressionRatio: recentMetrics.map(m => m.performance.compressionRatio),
      loadTime: recentMetrics.map(m => m.performance.loadTime),
    };
  }

  /**
   * Store bundle history
   */
  private async storeBundleHistory(): Promise<void> {
    try {
      await AsyncStorage.setItem(
        '@bundle_history',
        JSON.stringify(this.bundleMetrics.slice(-20)) // Store only last 20
      );
    } catch (error) {
      console.error('Failed to store bundle history:', error);
    }
  }

  /**
   * Load bundle history
   */
  private async loadBundleHistory(): Promise<void> {
    try {
      const history = await AsyncStorage.getItem('@bundle_history');
      if (history) {
        this.bundleMetrics = JSON.parse(history);
      }
    } catch (error) {
      console.error('Failed to load bundle history:', error);
    }
  }

  // Helper methods for analysis
  private extractDependencies(code: string): string[] {
    const deps: string[] = [];
    const requireRegex = /require\(['"]([^'"]+)['"]\)/g;
    let match;
    
    while ((match = requireRegex.exec(code)) !== null) {
      deps.push(match[1]);
    }
    
    return [...new Set(deps)];
  }

  private extractExports(code: string): string[] {
    const exports: string[] = [];
    
    // Look for various export patterns
    const patterns = [
      /exports\.(\w+)/g,
      /module\.exports\.(\w+)/g,
      /export\s+(?:const|let|var|function|class)\s+(\w+)/g,
      /export\s*{\s*([^}]+)\s*}/g,
    ];

    patterns.forEach(pattern => {
      let match;
      while ((match = pattern.exec(code)) !== null) {
        if (match[1].includes(',')) {
          exports.push(...match[1].split(',').map(e => e.trim()));
        } else {
          exports.push(match[1]);
        }
      }
    });
    
    return [...new Set(exports)];
  }

  private extractImports(code: string): ImportInfo[] {
    const imports: ImportInfo[] = [];
    // Simplified import extraction
    // In reality, you'd need more sophisticated parsing
    return imports;
  }

  private isVendorModule(code: string, id: string): boolean {
    return code.includes('node_modules') || 
           /\d+/.test(id) && parseInt(id) > 1000; // High module IDs often indicate vendors
  }

  private getModuleName(id: string, code: string): string {
    // Extract module name from code or ID
    const pathMatch = code.match(/\/([^\/]+)\.js/);
    return pathMatch ? pathMatch[1] : `module_${id}`;
  }

  private getModulePath(id: string): string {
    return `module://${id}`;
  }

  private estimateUsage(module: ModuleInfo): number {
    // Simplified usage estimation
    return Math.floor(Math.random() * 100);
  }

  private suggestAlternatives(moduleName: string): string[] {
    const alternatives: Record<string, string[]> = {
      'lodash': ['ramda', 'native-js-methods'],
      'moment': ['date-fns', 'dayjs'],
      'axios': ['fetch', 'ky'],
    };
    
    return alternatives[moduleName] || [];
  }

  private createUsageRecommendation(moduleName: string, usage: number): string {
    if (usage < 20) {
      return `Very low usage (${usage}%) - consider removing or replacing`;
    } else if (usage < 50) {
      return `Low usage (${usage}%) - look for lighter alternatives`;
    } else {
      return `Good usage (${usage}%) - consider tree shaking`;
    }
  }

  private estimateUnusedCode(module: ModuleInfo): number {
    // Simplified unused code estimation
    return Math.floor(Math.random() * 50);
  }

  private assessTreeShakingDifficulty(module: ModuleInfo): 'easy' | 'medium' | 'hard' {
    // Simplified difficulty assessment
    if (module.exports.length > 10) return 'hard';
    if (module.exports.length > 5) return 'medium';
    return 'easy';
  }

  private isCriticalPath(module: ModuleInfo): boolean {
    // Determine if module is on critical rendering path
    return module.isEntry || module.name.includes('navigation') || module.name.includes('auth');
  }

  /**
   * Clear analysis cache
   */
  clearCache(): void {
    this.analysisCache.clear();
  }

  /**
   * Get current bundle status
   */
  getStatus(): {
    tracking: boolean;
    metricsCount: number;
    cacheSize: number;
    lastAnalysis?: number;
  } {
    return {
      tracking: this.trackingEnabled,
      metricsCount: this.bundleMetrics.length,
      cacheSize: this.analysisCache.size,
      lastAnalysis: this.bundleMetrics[this.bundleMetrics.length - 1]?.timestamp,
    };
  }
}

export default BundleOptimizer.getInstance();