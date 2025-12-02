export { DeepLinkRouter } from './DeepLinkRouter';
export type {
  DeepLinkRoute,
  DeepLinkHandler,
  DeepLinkContext,
  DeepLinkResult
} from './DeepLinkRouter';

export { DeepLinkManager } from './DeepLinkManager';
export type { DeepLinkManagerConfig } from './DeepLinkManager';

export { default as DeepLinkingService } from '../DeepLinkingService';

// Export the default manager instance for convenience
import { DeepLinkManager } from './DeepLinkManager';
export default DeepLinkManager.getInstance();