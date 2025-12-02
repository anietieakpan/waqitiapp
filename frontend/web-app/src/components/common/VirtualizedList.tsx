import React, { useMemo, useCallback, useState, useRef, useEffect } from 'react';
import { FixedSizeList as List, ListChildComponentProps } from 'react-window';
import AutoSizer from 'react-virtualized-auto-sizer';
import {
  Box,
  TextField,
  InputAdornment,
  Typography,
  Skeleton,
  Alert,
  CircularProgress,
  Fade,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';;
import { debounce } from 'lodash';

export interface VirtualizedListItem {
  id: string | number;
  [key: string]: any;
}

export interface VirtualizedListProps<T extends VirtualizedListItem> {
  items: T[];
  renderItem: (item: T, index: number, style: React.CSSProperties) => React.ReactNode;
  itemHeight?: number;
  height?: number;
  width?: string | number;
  searchable?: boolean;
  searchKeys?: (keyof T)[];
  searchPlaceholder?: string;
  filterable?: boolean;
  filterFn?: (item: T, searchTerm: string) => boolean;
  onItemClick?: (item: T, index: number) => void;
  loading?: boolean;
  error?: string;
  emptyMessage?: string;
  loadingItems?: number;
  overscan?: number;
  className?: string;
  headerComponent?: React.ReactNode;
  footerComponent?: React.ReactNode;
  onLoadMore?: () => void;
  hasNextPage?: boolean;
  isLoadingMore?: boolean;
}

const DEFAULT_ITEM_HEIGHT = 80;
const DEFAULT_HEIGHT = 400;
const DEFAULT_LOADING_ITEMS = 10;
const DEFAULT_OVERSCAN = 5;

function VirtualizedList<T extends VirtualizedListItem>({
  items,
  renderItem,
  itemHeight = DEFAULT_ITEM_HEIGHT,
  height = DEFAULT_HEIGHT,
  width = '100%',
  searchable = false,
  searchKeys = [],
  searchPlaceholder = 'Search...',
  filterable = false,
  filterFn,
  onItemClick,
  loading = false,
  error,
  emptyMessage = 'No items to display',
  loadingItems = DEFAULT_LOADING_ITEMS,
  overscan = DEFAULT_OVERSCAN,
  className,
  headerComponent,
  footerComponent,
  onLoadMore,
  hasNextPage = false,
  isLoadingMore = false,
}: VirtualizedListProps<T>) {
  const [searchTerm, setSearchTerm] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const listRef = useRef<List>(null);
  const searchTimeoutRef = useRef<NodeJS.Timeout>();

  // Memoized filtered items
  const filteredItems = useMemo(() => {
    if (!searchable || !searchTerm.trim()) {
      return items;
    }

    const term = searchTerm.toLowerCase();
    
    if (filterFn) {
      return items.filter(item => filterFn(item, term));
    }

    // Default search implementation
    if (searchKeys.length > 0) {
      return items.filter(item =>
        searchKeys.some(key => {
          const value = item[key];
          if (value == null) return false;
          return String(value).toLowerCase().includes(term);
        })
      );
    }

    // Fallback: search all string properties
    return items.filter(item =>
      Object.values(item).some(value => {
        if (typeof value === 'string') {
          return value.toLowerCase().includes(term);
        }
        return false;
      })
    );
  }, [items, searchTerm, searchKeys, filterFn, searchable]);

  // Debounced search handler
  const handleSearchChange = useCallback(
    debounce((value: string) => {
      setSearchTerm(value);
      setIsSearching(false);
    }, 300),
    []
  );

  const handleSearchInputChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setIsSearching(true);
    
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }
    
    searchTimeoutRef.current = setTimeout(() => {
      handleSearchChange(value);
    }, 300);
  }, [handleSearchChange]);

  // Clear search
  const clearSearch = useCallback(() => {
    setSearchTerm('');
    setIsSearching(false);
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }
  }, []);

  // Infinite scroll handler
  const handleScroll = useCallback(
    ({ visibleStopIndex }: { visibleStopIndex: number }) => {
      if (
        onLoadMore &&
        hasNextPage &&
        !isLoadingMore &&
        visibleStopIndex >= filteredItems.length - 5
      ) {
        onLoadMore();
      }
    },
    [onLoadMore, hasNextPage, isLoadingMore, filteredItems.length]
  );

  // Row renderer with click handling
  const Row = useCallback(
    ({ index, style }: ListChildComponentProps) => {
      const item = filteredItems[index];
      
      if (!item) {
        return (
          <div style={style}>
            <Skeleton height={itemHeight} variant="rectangular" />
          </div>
        );
      }

      const handleClick = () => {
        if (onItemClick) {
          onItemClick(item, index);
        }
      };

      return (
        <div
          style={style}
          onClick={handleClick}
          role={onItemClick ? 'button' : undefined}
          tabIndex={onItemClick ? 0 : undefined}
          onKeyDown={onItemClick ? (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handleClick();
            }
          } : undefined}
          className={onItemClick ? 'virtualized-list-item-clickable' : ''}
        >
          {renderItem(item, index, style)}
        </div>
      );
    },
    [filteredItems, renderItem, onItemClick, itemHeight]
  );

  // Loading skeleton renderer
  const LoadingSkeleton = useCallback(() => (
    <Box>
      {Array.from({ length: loadingItems }, (_, index) => (
        <Box key={index} sx={{ p: 2 }}>
          <Skeleton height={itemHeight} variant="rectangular" />
        </Box>
      ))}
    </Box>
  ), [loadingItems, itemHeight]);

  // Empty state renderer
  const EmptyState = useCallback(() => (
    <Box
      display="flex"
      flexDirection="column"
      alignItems="center"
      justifyContent="center"
      height={height}
      p={3}
    >
      <Typography variant="h6" color="text.secondary" gutterBottom>
        {searchTerm ? 'No search results' : emptyMessage}
      </Typography>
      {searchTerm && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Try adjusting your search terms
        </Typography>
      )}
    </Box>
  ), [searchTerm, emptyMessage, height]);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current);
      }
    };
  }, []);

  if (error) {
    return (
      <Alert severity="error" sx={{ width: '100%' }}>
        {error}
      </Alert>
    );
  }

  if (loading) {
    return <LoadingSkeleton />;
  }

  const totalItems = filteredItems.length + (isLoadingMore ? 1 : 0);
  const shouldShowEmpty = !loading && filteredItems.length === 0;

  return (
    <Box className={className} sx={{ width, height }}>
      {/* Header */}
      {headerComponent && (
        <Box sx={{ mb: 2 }}>
          {headerComponent}
        </Box>
      )}

      {/* Search */}
      {searchable && (
        <Box sx={{ mb: 2 }}>
          <TextField
            fullWidth
            size="small"
            placeholder={searchPlaceholder}
            onChange={handleSearchInputChange}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search />
                </InputAdornment>
              ),
              endAdornment: isSearching && (
                <InputAdornment position="end">
                  <CircularProgress size={20} />
                </InputAdornment>
              ),
            }}
          />
          {searchTerm && (
            <Fade in={true}>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                {filteredItems.length} result{filteredItems.length !== 1 ? 's' : ''} found
              </Typography>
            </Fade>
          )}
        </Box>
      )}

      {/* Empty State */}
      {shouldShowEmpty ? (
        <EmptyState />
      ) : (
        /* Virtualized List */
        <AutoSizer>
          {({ width: autoWidth, height: autoHeight }) => (
            <List
              ref={listRef}
              width={autoWidth}
              height={autoHeight}
              itemCount={totalItems}
              itemSize={itemHeight}
              overscanCount={overscan}
              onItemsRendered={handleScroll}
            >
              {({ index, style }) => {
                // Show loading indicator for the last item if loading more
                if (isLoadingMore && index === filteredItems.length) {
                  return (
                    <div style={style}>
                      <Box 
                        display="flex" 
                        justifyContent="center" 
                        alignItems="center"
                        height={itemHeight}
                      >
                        <CircularProgress size={24} />
                        <Typography variant="body2" sx={{ ml: 1 }}>
                          Loading more...
                        </Typography>
                      </Box>
                    </div>
                  );
                }
                
                return <Row index={index} style={style} />;
              }}
            </List>
          )}
        </AutoSizer>
      )}

      {/* Footer */}
      {footerComponent && (
        <Box sx={{ mt: 2 }}>
          {footerComponent}
        </Box>
      )}

      {/* Styles for clickable items */}
      <style jsx>{`
        .virtualized-list-item-clickable {
          cursor: pointer;
          transition: background-color 0.2s ease;
        }
        .virtualized-list-item-clickable:hover {
          background-color: rgba(0, 0, 0, 0.04);
        }
        .virtualized-list-item-clickable:focus {
          outline: 2px solid #1976d2;
          outline-offset: -2px;
        }
      `}</style>
    </Box>
  );
}

export default VirtualizedList;