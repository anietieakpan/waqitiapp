export { default as WidgetService } from './WidgetService';
export type {
  WidgetData,
  WidgetConfig
} from './WidgetService';

// Export widget components
export { default as WidgetConfiguration } from '../../components/widgets/WidgetConfiguration';
export { default as WidgetPreview } from '../../components/widgets/WidgetPreview';

// Export widget hooks
export {
  useWidgets,
  useWidgetData,
  useWidgetConfiguration
} from '../../hooks/useWidgets';

// Export widget screen
export { default as WidgetSettingsScreen } from '../../screens/settings/WidgetSettingsScreen';