import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  Box,
  Skeleton,
  Alert,
  IconButton,
} from '@mui/material';
import BrokenImageIcon from '@mui/icons-material/BrokenImage';
import RefreshIcon from '@mui/icons-material/Refresh';;

interface LazyImageProps {
  src: string;
  alt: string;
  width?: number | string;
  height?: number | string;
  className?: string;
  placeholder?: React.ReactNode;
  errorComponent?: React.ReactNode;
  onLoad?: () => void;
  onError?: (error: Event) => void;
  threshold?: number;
  blur?: boolean;
  retryable?: boolean;
  maxRetries?: number;
  style?: React.CSSProperties;
  objectFit?: 'contain' | 'cover' | 'fill' | 'none' | 'scale-down';
}

interface LazyImageState {
  loaded: boolean;
  error: boolean;
  inView: boolean;
  retryCount: number;
}

const LazyImage: React.FC<LazyImageProps> = ({
  src,
  alt,
  width = '100%',
  height = 200,
  className,
  placeholder,
  errorComponent,
  onLoad,
  onError,
  threshold = 0.1,
  blur = true,
  retryable = true,
  maxRetries = 3,
  style,
  objectFit = 'cover',
}) => {
  const [state, setState] = useState<LazyImageState>({
    loaded: false,
    error: false,
    inView: false,
    retryCount: 0,
  });

  const imgRef = useRef<HTMLImageElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const observerRef = useRef<IntersectionObserver | null>(null);

  // Create intersection observer
  useEffect(() => {
    if (!containerRef.current) return;

    observerRef.current = new IntersectionObserver(
      (entries) => {
        const [entry] = entries;
        if (entry.isIntersecting && !state.inView) {
          setState(prev => ({ ...prev, inView: true }));
        }
      },
      {
        threshold,
        rootMargin: '50px',
      }
    );

    observerRef.current.observe(containerRef.current);

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect();
      }
    };
  }, [threshold, state.inView]);

  // Handle image load
  const handleLoad = useCallback(() => {
    setState(prev => ({ ...prev, loaded: true, error: false }));
    onLoad?.();
  }, [onLoad]);

  // Handle image error
  const handleError = useCallback((event: React.SyntheticEvent<HTMLImageElement, Event>) => {
    setState(prev => ({ 
      ...prev, 
      error: true, 
      loaded: false,
      retryCount: prev.retryCount + 1,
    }));
    onError?.(event.nativeEvent);
  }, [onError]);

  // Retry loading image
  const handleRetry = useCallback(() => {
    if (state.retryCount < maxRetries) {
      setState(prev => ({ 
        ...prev, 
        error: false, 
        loaded: false,
      }));
      
      // Force image reload by changing src temporarily
      if (imgRef.current) {
        const originalSrc = imgRef.current.src;
        imgRef.current.src = '';
        setTimeout(() => {
          if (imgRef.current) {
            imgRef.current.src = originalSrc;
          }
        }, 10);
      }
    }
  }, [state.retryCount, maxRetries]);

  // Default placeholder
  const defaultPlaceholder = (
    <Skeleton
      variant="rectangular"
      width={width}
      height={height}
      animation="wave"
    />
  );

  // Default error component
  const defaultErrorComponent = (
    <Box
      display="flex"
      flexDirection="column"
      alignItems="center"
      justifyContent="center"
      width={width}
      height={height}
      bgcolor="grey.100"
      color="grey.500"
      border={1}
      borderColor="grey.300"
      borderRadius={1}
    >
      <BrokenImage sx={{ fontSize: 40, mb: 1 }} />
      <Box fontSize="0.8rem" textAlign="center" px={1}>
        Failed to load image
        {retryable && state.retryCount < maxRetries && (
          <IconButton size="small" onClick={handleRetry} sx={{ ml: 1 }}>
            <Refresh fontSize="small" />
          </IconButton>
        )}
      </Box>
    </Box>
  );

  // Don't load image until it's in view
  if (!state.inView) {
    return (
      <Box
        ref={containerRef}
        className={className}
        style={{
          width,
          height,
          ...style,
        }}
      >
        {placeholder || defaultPlaceholder}
      </Box>
    );
  }

  // Show error state
  if (state.error && state.retryCount >= maxRetries) {
    return errorComponent || defaultErrorComponent;
  }

  return (
    <Box
      ref={containerRef}
      className={className}
      style={{
        width,
        height,
        position: 'relative',
        overflow: 'hidden',
        ...style,
      }}
    >
      {/* Placeholder while loading */}
      {!state.loaded && !state.error && (
        <Box
          position="absolute"
          top={0}
          left={0}
          right={0}
          bottom={0}
          zIndex={1}
        >
          {placeholder || defaultPlaceholder}
        </Box>
      )}

      {/* Error state with retry */}
      {state.error && state.retryCount < maxRetries && (
        <Box
          position="absolute"
          top={0}
          left={0}
          right={0}
          bottom={0}
          zIndex={1}
        >
          {defaultErrorComponent}
        </Box>
      )}

      {/* Actual image */}
      <img
        ref={imgRef}
        src={src}
        alt={alt}
        onLoad={handleLoad}
        onError={handleError}
        style={{
          width: '100%',
          height: '100%',
          objectFit,
          display: state.loaded ? 'block' : 'none',
          filter: blur && !state.loaded ? 'blur(4px)' : 'none',
          transition: 'filter 0.3s ease',
        }}
      />
    </Box>
  );
};

export default LazyImage;