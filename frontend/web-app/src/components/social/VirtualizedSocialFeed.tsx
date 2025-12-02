import React, { memo, useCallback, useMemo } from 'react';
import { FixedSizeList as List } from 'react-window';
import InfiniteLoader from 'react-window-infinite-loader';
import { Box, CircularProgress, Typography } from '@mui/material';
import { SocialFeedItem as SocialFeedItemType } from '../../services/socialService';
import SocialFeedItem from './SocialFeedItem';

interface VirtualizedSocialFeedProps {
  items: SocialFeedItemType[];
  hasNextPage: boolean;
  isLoading: boolean;
  loadMore: () => Promise<void>;
  currentUserId: string;
  onLike: (itemId: string) => void;
  onComment: (itemId: string) => void;
  onShare: (itemId: string) => void;
  onMenuOpen: (itemId: string, anchorEl: HTMLElement) => void;
  height?: number;
  itemHeight?: number;
}

const VirtualizedSocialFeed = memo<VirtualizedSocialFeedProps>(({
  items,
  hasNextPage,
  isLoading,
  loadMore,
  currentUserId,
  onLike,
  onComment,
  onShare,
  onMenuOpen,
  height = 600,
  itemHeight = 200
}) => {
  const itemCount = hasNextPage ? items.length + 1 : items.length;

  const isItemLoaded = useCallback((index: number) => {
    return !!items[index];
  }, [items]);

  const loadMoreItems = useCallback(async () => {
    if (!isLoading && hasNextPage) {
      await loadMore();
    }
  }, [isLoading, hasNextPage, loadMore]);

  const itemData = useMemo(() => ({
    items,
    currentUserId,
    onLike,
    onComment,
    onShare,
    onMenuOpen,
    isLoading
  }), [items, currentUserId, onLike, onComment, onShare, onMenuOpen, isLoading]);

  if (items.length === 0 && !isLoading) {
    return (
      <Box 
        sx={{ 
          display: 'flex', 
          flexDirection: 'column',
          alignItems: 'center', 
          justifyContent: 'center',
          height: 300,
          textAlign: 'center'
        }}
      >
        <Typography variant="h6" color="text.secondary" gutterBottom>
          No activity yet
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Follow some friends or make a payment to see activity here
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ height, width: '100%' }}>
      <InfiniteLoader
        isItemLoaded={isItemLoaded}
        itemCount={itemCount}
        loadMoreItems={loadMoreItems}
        threshold={3} // Load more items when 3 items from the end
      >
        {({ onItemsRendered, ref }) => (
          <List
            ref={ref}
            height={height}
            itemCount={itemCount}
            itemSize={itemHeight}
            onItemsRendered={onItemsRendered}
            itemData={itemData}
            overscanCount={5} // Pre-render 5 items above and below visible area
          >
            {VirtualizedItem}
          </List>
        )}
      </InfiniteLoader>
    </Box>
  );
});

// Virtualized item component
const VirtualizedItem = memo<any>(({ index, style, data }) => {
  const { items, currentUserId, onLike, onComment, onShare, onMenuOpen, isLoading } = data;
  const item = items[index];

  // Loading item
  if (!item) {
    return (
      <div style={style}>
        <Box 
          sx={{ 
            display: 'flex', 
            justifyContent: 'center', 
            alignItems: 'center',
            height: '100%'
          }}
        >
          <CircularProgress size={24} />
        </Box>
      </div>
    );
  }

  return (
    <div style={style}>
      <Box sx={{ p: 1, height: '100%', overflow: 'hidden' }}>
        <SocialFeedItem
          item={item}
          currentUserId={currentUserId}
          onLike={onLike}
          onComment={onComment}
          onShare={onShare}
          onMenuOpen={onMenuOpen}
        />
      </Box>
    </div>
  );
});

VirtualizedItem.displayName = 'VirtualizedItem';
VirtualizedSocialFeed.displayName = 'VirtualizedSocialFeed';

export default VirtualizedSocialFeed;