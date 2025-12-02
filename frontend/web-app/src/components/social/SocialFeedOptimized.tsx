import React, { useState, useCallback, useMemo } from 'react';
import { Box, Menu, MenuItem, Skeleton } from '@mui/material';
import { useInfiniteQuery, useMutation, useQueryClient } from 'react-query';
import { toast } from 'react-toastify';
import { useAppSelector } from '../../hooks/redux';
import { socialService, SocialFeedItem, SocialFeedResponse } from '../../services/socialService';
import SocialFeedHeader from './SocialFeedHeader';
import VirtualizedSocialFeed from './VirtualizedSocialFeed';
import SocialFeedComments from './SocialFeedComments';

interface SocialFeedOptimizedProps {
  filter?: 'all' | 'following' | 'trending';
  userId?: string;
}

const SocialFeedOptimized: React.FC<SocialFeedOptimizedProps> = ({ 
  filter = 'all', 
  userId 
}) => {
  const { user } = useAppSelector((state) => state.auth);
  const queryClient = useQueryClient();
  
  const [selectedTab, setSelectedTab] = useState(filter);
  const [menuAnchorEl, setMenuAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [commentDialogOpen, setCommentDialogOpen] = useState(false);

  // Infinite query for feed data with optimized caching
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isLoading,
    isFetchingNextPage,
    refetch
  } = useInfiniteQuery({
    queryKey: ['socialFeed', selectedTab, userId],
    queryFn: ({ pageParam = 0 }) => socialService.getFeed({
      filter: selectedTab,
      userId,
      page: pageParam,
      limit: 20
    }),
    getNextPageParam: (lastPage: SocialFeedResponse) => 
      lastPage.hasMore ? lastPage.page + 1 : undefined,
    staleTime: 2 * 60 * 1000, // 2 minutes
    cacheTime: 10 * 60 * 1000, // 10 minutes
    refetchOnWindowFocus: false,
    keepPreviousData: true,
  });

  // Flatten pages into single array
  const allItems = useMemo(() => 
    data?.pages.flatMap(page => page.items) ?? [],
    [data]
  );

  // Like mutation with optimistic updates
  const likeMutation = useMutation({
    mutationFn: (itemId: string) => socialService.toggleLike(itemId),
    onMutate: async (itemId) => {
      await queryClient.cancelQueries(['socialFeed']);
      
      const previousData = queryClient.getQueryData(['socialFeed', selectedTab, userId]);
      
      // Optimistically update the cache
      queryClient.setQueryData(['socialFeed', selectedTab, userId], (old: any) => {
        if (!old) return old;
        
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            items: page.items.map((item: SocialFeedItem) => 
              item.id === itemId 
                ? {
                    ...item,
                    liked: !item.liked,
                    likesCount: item.liked 
                      ? (item.likesCount || 0) - 1 
                      : (item.likesCount || 0) + 1
                  }
                : item
            )
          }))
        };
      });

      return { previousData };
    },
    onError: (err, itemId, context) => {
      // Revert optimistic update on error
      if (context?.previousData) {
        queryClient.setQueryData(['socialFeed', selectedTab, userId], context.previousData);
      }
      toast.error('Failed to update like');
    },
    onSettled: () => {
      queryClient.invalidateQueries(['socialFeed']);
    },
  });

  // Event handlers
  const handleTabChange = useCallback((tab: string) => {
    setSelectedTab(tab);
  }, []);

  const handleRefresh = useCallback(() => {
    refetch();
  }, [refetch]);

  const handleLike = useCallback((itemId: string) => {
    likeMutation.mutate(itemId);
  }, [likeMutation]);

  const handleComment = useCallback((itemId: string) => {
    setSelectedItemId(itemId);
    setCommentDialogOpen(true);
  }, []);

  const handleShare = useCallback((itemId: string) => {
    if (navigator.share) {
      navigator.share({
        title: 'Waqiti Social Feed',
        text: 'Check out this activity',
        url: `${window.location.origin}/social/${itemId}`,
      });
    } else {
      navigator.clipboard.writeText(`${window.location.origin}/social/${itemId}`);
      toast.success('Link copied to clipboard');
    }
  }, []);

  const handleMenuOpen = useCallback((itemId: string, anchorEl: HTMLElement) => {
    setSelectedItemId(itemId);
    setMenuAnchorEl(anchorEl);
  }, []);

  const handleMenuClose = useCallback(() => {
    setMenuAnchorEl(null);
    setSelectedItemId(null);
  }, []);

  const loadMore = useCallback(async () => {
    if (hasNextPage && !isFetchingNextPage) {
      await fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (isLoading && allItems.length === 0) {
    return (
      <Box>
        <Skeleton variant="rectangular" height={60} sx={{ mb: 2 }} />
        {Array.from({ length: 5 }).map((_, index) => (
          <Skeleton 
            key={index} 
            variant="rectangular" 
            height={200} 
            sx={{ mb: 2, borderRadius: 1 }} 
          />
        ))}
      </Box>
    );
  }

  return (
    <Box>
      <SocialFeedHeader
        selectedTab={selectedTab}
        onTabChange={handleTabChange}
        onRefresh={handleRefresh}
        isLoading={isLoading}
        lastUpdated={new Date()}
      />

      <VirtualizedSocialFeed
        items={allItems}
        hasNextPage={hasNextPage || false}
        isLoading={isFetchingNextPage}
        loadMore={loadMore}
        currentUserId={user?.id || ''}
        onLike={handleLike}
        onComment={handleComment}
        onShare={handleShare}
        onMenuOpen={handleMenuOpen}
        height={800}
        itemHeight={220}
      />

      {/* Context Menu */}
      <Menu
        anchorEl={menuAnchorEl}
        open={Boolean(menuAnchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleMenuClose}>
          Report
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          Hide
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          Copy Link
        </MenuItem>
      </Menu>

      {/* Comments Dialog */}
      {selectedItemId && (
        <SocialFeedComments
          itemId={selectedItemId}
          open={commentDialogOpen}
          onClose={() => {
            setCommentDialogOpen(false);
            setSelectedItemId(null);
          }}
        />
      )}
    </Box>
  );
};

export default SocialFeedOptimized;