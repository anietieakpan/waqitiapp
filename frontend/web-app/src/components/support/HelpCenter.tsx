import React, { useState, useEffect } from 'react';
import { sanitizeArticleHTML, sanitizeIconHTML } from '../../utils/sanitize';
import {
  Box,
  Container,
  Typography,
  TextField,
  Card,
  CardContent,
  Grid,
  Chip,
  Avatar,
  Button,
  IconButton,
  InputAdornment,
  Breadcrumbs,
  Link,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider,
  Paper,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Rating,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Skeleton,
  Badge,
  useTheme,
  alpha,
  Fade,
  Zoom,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ArticleIcon from '@mui/icons-material/Article';
import QuestionAnswerIcon from '@mui/icons-material/QuestionAnswer';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import StarIcon from '@mui/icons-material/Star';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';
import ShareIcon from '@mui/icons-material/Share';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import ScheduleIcon from '@mui/icons-material/Schedule';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import VideoLibraryIcon from '@mui/icons-material/VideoLibrary';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import DownloadIcon from '@mui/icons-material/Download';
import PrintIcon from '@mui/icons-material/Print';
import VisibilityIcon from '@mui/icons-material/Visibility';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import CategoryIcon from '@mui/icons-material/Category';
import LanguageIcon from '@mui/icons-material/Language';
import FilterListIcon from '@mui/icons-material/FilterList';
import SortIcon from '@mui/icons-material/Sort';
import CloseIcon from '@mui/icons-material/Close';;
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import { motion, AnimatePresence } from 'framer-motion';

import { supportService } from '../../services/supportService';
import { formatDate, getRelativeTime } from '../../utils/formatters';

interface Article {
  id: string;
  slug: string;
  title: string;
  summary: string;
  content: string;
  contentHtml: string;
  categoryId: string;
  categoryName: string;
  tags: string[];
  authorName: string;
  viewCount: number;
  helpfulCount: number;
  notHelpfulCount: number;
  averageRating: number;
  isFeatured: boolean;
  estimatedReadTime: number;
  difficultyLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
  languageCode: string;
  availableLanguages: string[];
  videoUrl?: string;
  createdAt: string;
  updatedAt: string;
  publishedAt: string;
}

interface Category {
  id: string;
  name: string;
  slug: string;
  description: string;
  icon: string;
  articleCount: number;
  displayOrder: number;
  subCategories: Category[];
}

export const HelpCenter: React.FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { categorySlug, articleSlug } = useParams();
  
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [bookmarkedArticles, setBookmarkedArticles] = useState<Set<string>>(new Set());
  const [feedbackDialog, setFeedbackDialog] = useState<{open: boolean, articleId: string}>({
    open: false,
    articleId: ''
  });
  const [contactDialog, setContactDialog] = useState(false);
  const [sortBy, setSortBy] = useState<'relevance' | 'date' | 'popularity'>('relevance');
  const [filterDialogOpen, setFilterDialogOpen] = useState(false);

  // Fetch categories
  const { data: categories, isLoading: categoriesLoading } = useQuery(
    'help-categories',
    () => supportService.getKnowledgeCategories()
  );

  // Fetch featured articles
  const { data: featuredArticles, isLoading: featuredLoading } = useQuery(
    'featured-articles',
    () => supportService.getFeaturedArticles('en', 6)
  );

  // Fetch popular articles
  const { data: popularArticles } = useQuery(
    'popular-articles',
    () => supportService.getPopularArticles('en', 10)
  );

  // Search articles
  const { data: searchResults, isLoading: searchLoading } = useQuery(
    ['search-articles', searchTerm, selectedCategory, sortBy],
    () => supportService.searchArticles({
      query: searchTerm,
      category: selectedCategory || undefined,
      sortBy,
      includeInternal: false,
    }),
    {
      enabled: searchTerm.length > 0,
      keepPreviousData: true,
    }
  );

  // Get single article
  const { data: currentArticle, isLoading: articleLoading } = useQuery(
    ['article', articleSlug],
    () => supportService.getArticleBySlug(articleSlug!, 'en'),
    {
      enabled: !!articleSlug,
    }
  );

  // Get category articles
  const { data: categoryArticles, isLoading: categoryLoading } = useQuery(
    ['category-articles', categorySlug],
    () => supportService.getArticlesByCategory(categorySlug!),
    {
      enabled: !!categorySlug && !articleSlug,
    }
  );

  // Submit feedback mutation
  const feedbackMutation = useMutation(
    ({ articleId, feedback }: { articleId: string; feedback: any }) =>
      supportService.submitArticleFeedback(articleId, feedback),
    {
      onSuccess: () => {
        toast.success('Thank you for your feedback!');
        setFeedbackDialog({ open: false, articleId: '' });
        queryClient.invalidateQueries(['article', articleSlug]);
      },
      onError: () => {
        toast.error('Failed to submit feedback');
      },
    }
  );

  const handleSearch = (query: string) => {
    setSearchTerm(query);
    if (query && !searchTerm) {
      navigate('/help/search');
    }
  };

  const handleCategorySelect = (category: Category) => {
    setSelectedCategory(category.id);
    navigate(`/help/category/${category.slug}`);
  };

  const handleArticleClick = (article: Article) => {
    navigate(`/help/category/${article.categoryName.toLowerCase()}/article/${article.slug}`);
  };

  const toggleBookmark = (articleId: string) => {
    const newBookmarks = new Set(bookmarkedArticles);
    if (newBookmarks.has(articleId)) {
      newBookmarks.delete(articleId);
      toast.success('Removed from bookmarks');
    } else {
      newBookmarks.add(articleId);
      toast.success('Added to bookmarks');
    }
    setBookmarkedArticles(newBookmarks);
  };

  const handleFeedback = (articleId: string, isHelpful: boolean) => {
    feedbackMutation.mutate({
      articleId,
      feedback: {
        isHelpful,
        userId: 'current-user-id', // Get from auth context
      },
    });
  };

  const getDifficultyColor = (level: string) => {
    switch (level) {
      case 'BEGINNER': return 'success';
      case 'INTERMEDIATE': return 'warning';
      case 'ADVANCED': return 'error';
      case 'EXPERT': return 'primary';
      default: return 'default';
    }
  };

  const renderSearchResults = () => {
    if (!searchTerm) return null;

    if (searchLoading) {
      return (
        <Grid container spacing={3}>
          {[...Array(6)].map((_, index) => (
            <Grid item xs={12} md={6} key={index}>
              <Skeleton variant="rectangular" height={200} />
            </Grid>
          ))}
        </Grid>
      );
    }

    if (!searchResults?.articles?.length) {
      return (
        <Paper sx={{ p: 6, textAlign: 'center' }}>
          <HelpOutline sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" gutterBottom>
            No articles found for "{searchTerm}"
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Try searching with different keywords or browse our categories below.
          </Typography>
          <Button variant="contained" onClick={() => setContactDialog(true)}>
            Contact Support
          </Button>
        </Paper>
      );
    }

    return (
      <Grid container spacing={3}>
        {searchResults.articles.map((article: Article) => (
          <Grid item xs={12} md={6} key={article.id}>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
            >
              <Card 
                sx={{ 
                  height: '100%', 
                  cursor: 'pointer',
                  '&:hover': {
                    boxShadow: theme.shadows[8],
                    transform: 'translateY(-2px)',
                  },
                  transition: 'all 0.2s ease-in-out',
                }}
                onClick={() => handleArticleClick(article)}
              >
                <CardContent>
                  <Box display="flex" justifyContent="space-between" alignItems="start" mb={2}>
                    <Box>
                      <Chip
                        label={article.categoryName}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                      <Chip
                        label={article.difficultyLevel}
                        size="small"
                        color={getDifficultyColor(article.difficultyLevel) as any}
                        sx={{ ml: 1 }}
                      />
                    </Box>
                    <IconButton
                      size="small"
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleBookmark(article.id);
                      }}
                    >
                      {bookmarkedArticles.has(article.id) ? <Bookmark /> : <BookmarkBorder />}
                    </IconButton>
                  </Box>
                  
                  <Typography variant="h6" gutterBottom>
                    {article.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" paragraph>
                    {article.summary}
                  </Typography>
                  
                  <Box display="flex" alignItems="center" gap={2} mt={2}>
                    <Box display="flex" alignItems="center">
                      <Visibility fontSize="small" sx={{ mr: 0.5 }} />
                      <Typography variant="caption">
                        {article.viewCount} views
                      </Typography>
                    </Box>
                    <Box display="flex" alignItems="center">
                      <AccessTime fontSize="small" sx={{ mr: 0.5 }} />
                      <Typography variant="caption">
                        {article.estimatedReadTime} min read
                      </Typography>
                    </Box>
                    <Box display="flex" alignItems="center">
                      <Star fontSize="small" sx={{ mr: 0.5 }} />
                      <Typography variant="caption">
                        {article.averageRating?.toFixed(1) || 'N/A'}
                      </Typography>
                    </Box>
                  </Box>
                  
                  {article.tags && article.tags.length > 0 && (
                    <Box mt={2}>
                      {article.tags.slice(0, 3).map((tag) => (
                        <Chip
                          key={tag}
                          label={tag}
                          size="small"
                          variant="outlined"
                          sx={{ mr: 0.5, mb: 0.5 }}
                        />
                      ))}
                    </Box>
                  )}
                </CardContent>
              </Card>
            </motion.div>
          </Grid>
        ))}
      </Grid>
    );
  };

  const renderArticleView = () => {
    if (!currentArticle) return null;

    return (
      <Container maxWidth="md">
        <Breadcrumbs sx={{ mb: 3 }}>
          <Link href="/help" underline="hover">
            Help Center
          </Link>
          <Link href={`/help/category/${currentArticle.categoryName.toLowerCase()}`} underline="hover">
            {currentArticle.categoryName}
          </Link>
          <Typography color="text.primary">{currentArticle.title}</Typography>
        </Breadcrumbs>

        <Paper sx={{ p: 4 }}>
          <Box display="flex" justifyContent="space-between" alignItems="start" mb={3}>
            <Box>
              <Typography variant="h4" gutterBottom>
                {currentArticle.title}
              </Typography>
              <Box display="flex" alignItems="center" gap={2} mb={2}>
                <Chip
                  label={currentArticle.categoryName}
                  color="primary"
                  variant="outlined"
                />
                <Chip
                  label={currentArticle.difficultyLevel}
                  color={getDifficultyColor(currentArticle.difficultyLevel) as any}
                />
                <Typography variant="body2" color="text.secondary">
                  {currentArticle.estimatedReadTime} min read
                </Typography>
              </Box>
            </Box>
            
            <Box display="flex" gap={1}>
              <IconButton onClick={() => toggleBookmark(currentArticle.id)}>
                {bookmarkedArticles.has(currentArticle.id) ? <Bookmark /> : <BookmarkBorder />}
              </IconButton>
              <IconButton onClick={() => window.print()}>
                <Print />
              </IconButton>
              <IconButton onClick={() => {
                if (navigator.share) {
                  navigator.share({
                    title: currentArticle.title,
                    url: window.location.href,
                  });
                } else {
                  navigator.clipboard.writeText(window.location.href);
                  toast.success('Link copied to clipboard');
                }
              }}>
                <Share />
              </IconButton>
            </Box>
          </Box>

          <Box display="flex" alignItems="center" gap={2} mb={4} pb={2} borderBottom={1} borderColor="divider">
            <Avatar sx={{ width: 32, height: 32 }}>
              {currentArticle.authorName[0]}
            </Avatar>
            <Box>
              <Typography variant="body2" fontWeight="medium">
                {currentArticle.authorName}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Updated {getRelativeTime(currentArticle.updatedAt)}
              </Typography>
            </Box>
            <Box ml="auto" display="flex" alignItems="center" gap={2}>
              <Box display="flex" alignItems="center">
                <Visibility fontSize="small" sx={{ mr: 0.5 }} />
                <Typography variant="caption">
                  {currentArticle.viewCount} views
                </Typography>
              </Box>
              <Rating value={currentArticle.averageRating || 0} readOnly size="small" />
            </Box>
          </Box>

          {currentArticle.videoUrl && (
            <Paper sx={{ p: 2, mb: 4, bgcolor: 'primary.light', color: 'primary.contrastText' }}>
              <Box display="flex" alignItems="center" gap={2}>
                <VideoLibrary />
                <Box>
                  <Typography variant="subtitle2">Video Tutorial Available</Typography>
                  <Typography variant="body2">
                    Watch the video version of this article for a visual walkthrough.
                  </Typography>
                </Box>
                <Button variant="contained" size="small" sx={{ ml: 'auto' }}>
                  Watch Video
                </Button>
              </Box>
            </Paper>
          )}

          <Box 
            dangerouslySetInnerHTML={{ __html: sanitizeArticleHTML(currentArticle.contentHtml) }}
            sx={{
              '& h1, & h2, & h3, & h4, & h5, & h6': {
                mt: 3,
                mb: 2,
                color: 'text.primary',
              },
              '& p': {
                mb: 2,
                lineHeight: 1.7,
              },
              '& ul, & ol': {
                mb: 2,
                pl: 3,
              },
              '& li': {
                mb: 1,
              },
              '& code': {
                backgroundColor: alpha(theme.palette.primary.main, 0.1),
                padding: '2px 6px',
                borderRadius: 1,
                fontFamily: 'monospace',
              },
              '& pre': {
                backgroundColor: alpha(theme.palette.primary.main, 0.05),
                padding: 2,
                borderRadius: 1,
                overflow: 'auto',
                mb: 2,
              },
              '& blockquote': {
                borderLeft: `4px solid ${theme.palette.primary.main}`,
                pl: 2,
                ml: 0,
                fontStyle: 'italic',
                color: 'text.secondary',
              },
            }}
          />

          {currentArticle.tags && currentArticle.tags.length > 0 && (
            <Box mt={4} pt={2} borderTop={1} borderColor="divider">
              <Typography variant="subtitle2" gutterBottom>
                Tags
              </Typography>
              <Box>
                {currentArticle.tags.map((tag) => (
                  <Chip
                    key={tag}
                    label={tag}
                    size="small"
                    variant="outlined"
                    sx={{ mr: 1, mb: 1 }}
                    onClick={() => handleSearch(tag)}
                  />
                ))}
              </Box>
            </Box>
          )}

          <Divider sx={{ my: 4 }} />

          <Box textAlign="center">
            <Typography variant="h6" gutterBottom>
              Was this article helpful?
            </Typography>
            <Box display="flex" justifyContent="center" gap={2} mb={3}>
              <Button
                variant="outlined"
                startIcon={<ThumbUp />}
                onClick={() => handleFeedback(currentArticle.id, true)}
                color="success"
              >
                Yes ({currentArticle.helpfulCount})
              </Button>
              <Button
                variant="outlined"
                startIcon={<ThumbDown />}
                onClick={() => handleFeedback(currentArticle.id, false)}
                color="error"
              >
                No ({currentArticle.notHelpfulCount})
              </Button>
            </Box>
            <Button
              variant="text"
              onClick={() => setFeedbackDialog({ open: true, articleId: currentArticle.id })}
            >
              Leave detailed feedback
            </Button>
          </Box>
        </Paper>
      </Container>
    );
  };

  const renderCategoryView = () => {
    const category = categories?.find((c: Category) => c.slug === categorySlug);
    
    if (!category) return null;

    return (
      <Container maxWidth="lg">
        <Breadcrumbs sx={{ mb: 3 }}>
          <Link href="/help" underline="hover">
            Help Center
          </Link>
          <Typography color="text.primary">{category.name}</Typography>
        </Breadcrumbs>

        <Box mb={4}>
          <Typography variant="h4" gutterBottom>
            {category.name}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {category.description}
          </Typography>
        </Box>

        {categoryLoading ? (
          <Grid container spacing={3}>
            {[...Array(6)].map((_, index) => (
              <Grid item xs={12} md={6} key={index}>
                <Skeleton variant="rectangular" height={200} />
              </Grid>
            ))}
          </Grid>
        ) : (
          <Grid container spacing={3}>
            {categoryArticles?.articles?.map((article: Article) => (
              <Grid item xs={12} md={6} key={article.id}>
                <Card 
                  sx={{ 
                    height: '100%', 
                    cursor: 'pointer',
                    '&:hover': { boxShadow: theme.shadows[8] },
                  }}
                  onClick={() => handleArticleClick(article)}
                >
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      {article.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" paragraph>
                      {article.summary}
                    </Typography>
                    <Box display="flex" alignItems="center" gap={2}>
                      <Typography variant="caption">
                        {article.estimatedReadTime} min read
                      </Typography>
                      <Chip
                        label={article.difficultyLevel}
                        size="small"
                        color={getDifficultyColor(article.difficultyLevel) as any}
                      />
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        )}
      </Container>
    );
  };

  const renderHomePage = () => (
    <Container maxWidth="lg">
      <Box textAlign="center" mb={6}>
        <Typography variant="h3" gutterBottom>
          How can we help you?
        </Typography>
        <Typography variant="h6" color="text.secondary" mb={4}>
          Search our knowledge base or browse categories below
        </Typography>
        
        <TextField
          fullWidth
          placeholder="Search for articles, FAQs, and more..."
          value={searchTerm}
          onChange={(e) => handleSearch(e.target.value)}
          sx={{ maxWidth: 600, mb: 2 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search />
              </InputAdornment>
            ),
            endAdornment: searchTerm && (
              <InputAdornment position="end">
                <IconButton onClick={() => setSearchTerm('')}>
                  <Close />
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
        
        <Box display="flex" justifyContent="center" gap={2} flexWrap="wrap">
          <Button
            variant="outlined"
            startIcon={<ChatBubbleOutline />}
            onClick={() => navigate('/support/chat')}
          >
            Live Chat
          </Button>
          <Button
            variant="outlined"
            startIcon={<Email />}
            onClick={() => setContactDialog(true)}
          >
            Contact Support
          </Button>
        </Box>
      </Box>

      {searchTerm ? (
        renderSearchResults()
      ) : (
        <>
          {/* Featured Articles */}
          <Box mb={6}>
            <Typography variant="h5" gutterBottom>
              Featured Articles
            </Typography>
            <Grid container spacing={3}>
              {featuredLoading ? (
                [...Array(3)].map((_, index) => (
                  <Grid item xs={12} md={4} key={index}>
                    <Skeleton variant="rectangular" height={200} />
                  </Grid>
                ))
              ) : (
                featuredArticles?.map((article: Article) => (
                  <Grid item xs={12} md={4} key={article.id}>
                    <Card 
                      sx={{ 
                        height: '100%', 
                        cursor: 'pointer',
                        position: 'relative',
                        '&:hover': { boxShadow: theme.shadows[8] },
                      }}
                      onClick={() => handleArticleClick(article)}
                    >
                      <Badge 
                        badgeContent="Featured" 
                        color="primary" 
                        sx={{ position: 'absolute', top: 8, right: 8, zIndex: 1 }}
                      />
                      <CardContent>
                        <Typography variant="h6" gutterBottom>
                          {article.title}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" paragraph>
                          {article.summary}
                        </Typography>
                        <Box display="flex" alignItems="center" gap={2}>
                          <Chip
                            label={article.categoryName}
                            size="small"
                            color="primary"
                            variant="outlined"
                          />
                          <Typography variant="caption">
                            {article.viewCount} views
                          </Typography>
                        </Box>
                      </CardContent>
                    </Card>
                  </Grid>
                ))
              )}
            </Grid>
          </Box>

          {/* Categories */}
          <Box mb={6}>
            <Typography variant="h5" gutterBottom>
              Browse by Category
            </Typography>
            <Grid container spacing={3}>
              {categoriesLoading ? (
                [...Array(6)].map((_, index) => (
                  <Grid item xs={12} sm={6} md={4} key={index}>
                    <Skeleton variant="rectangular" height={120} />
                  </Grid>
                ))
              ) : (
                categories?.map((category: Category) => (
                  <Grid item xs={12} sm={6} md={4} key={category.id}>
                    <Card 
                      sx={{ 
                        cursor: 'pointer',
                        '&:hover': { boxShadow: theme.shadows[4] },
                      }}
                      onClick={() => handleCategorySelect(category)}
                    >
                      <CardContent>
                        <Box display="flex" alignItems="center" mb={2}>
                          <Avatar sx={{ mr: 2, bgcolor: 'primary.light' }}>
                            {category.icon ? (
                              <span dangerouslySetInnerHTML={{ __html: sanitizeIconHTML(category.icon) }} />
                            ) : (
                              <Article />
                            )}
                          </Avatar>
                          <Box>
                            <Typography variant="h6">{category.name}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {category.articleCount} articles
                            </Typography>
                          </Box>
                        </Box>
                        <Typography variant="body2" color="text.secondary">
                          {category.description}
                        </Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                ))
              )}
            </Grid>
          </Box>

          {/* Popular Articles */}
          <Box mb={6}>
            <Typography variant="h5" gutterBottom>
              Popular Articles
            </Typography>
            <List>
              {popularArticles?.slice(0, 8).map((article: Article, index: number) => (
                <ListItem
                  key={article.id}
                  button
                  onClick={() => handleArticleClick(article)}
                  sx={{
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    mb: 1,
                    '&:hover': { bgcolor: 'action.hover' },
                  }}
                >
                  <ListItemIcon>
                    <Badge
                      badgeContent={index + 1}
                      color="primary"
                      sx={{ '& .MuiBadge-badge': { fontSize: '0.75rem' } }}
                    >
                      <TrendingUp />
                    </Badge>
                  </ListItemIcon>
                  <ListItemText
                    primary={article.title}
                    secondary={`${article.viewCount} views • ${article.estimatedReadTime} min read`}
                  />
                  <Box display="flex" alignItems="center" gap={1}>
                    <Star fontSize="small" />
                    <Typography variant="caption">
                      {article.averageRating?.toFixed(1) || 'N/A'}
                    </Typography>
                  </Box>
                </ListItem>
              ))}
            </List>
          </Box>
        </>
      )}

      {/* Contact Dialog */}
      <Dialog open={contactDialog} onClose={() => setContactDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Contact Support</DialogTitle>
        <DialogContent>
          <Typography variant="body2" paragraph>
            Can't find what you're looking for? Our support team is here to help.
          </Typography>
          
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <Card sx={{ p: 2, textAlign: 'center', cursor: 'pointer' }}
                    onClick={() => navigate('/support/chat')}>
                <ChatBubbleOutline sx={{ fontSize: 48, color: 'primary.main', mb: 1 }} />
                <Typography variant="h6">Live Chat</Typography>
                <Typography variant="body2" color="text.secondary">
                  Average response time: 2 minutes
                </Typography>
                <Chip label="Available Now" color="success" size="small" sx={{ mt: 1 }} />
              </Card>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Card sx={{ p: 2, textAlign: 'center', cursor: 'pointer' }}
                    onClick={() => navigate('/support/ticket')}>
                <Email sx={{ fontSize: 48, color: 'primary.main', mb: 1 }} />
                <Typography variant="h6">Submit Ticket</Typography>
                <Typography variant="body2" color="text.secondary">
                  Average response time: 4 hours
                </Typography>
                <Chip label="24/7 Available" color="primary" size="small" sx={{ mt: 1 }} />
              </Card>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setContactDialog(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Feedback Dialog */}
      <Dialog 
        open={feedbackDialog.open} 
        onClose={() => setFeedbackDialog({ open: false, articleId: '' })}
        maxWidth="sm" 
        fullWidth
      >
        <DialogTitle>Article Feedback</DialogTitle>
        <DialogContent>
          <Typography variant="body2" paragraph>
            Help us improve this article by providing detailed feedback.
          </Typography>
          
          <TextField
            fullWidth
            multiline
            rows={4}
            label="What could be improved?"
            placeholder="Tell us what was unclear, incorrect, or missing..."
            sx={{ mb: 2 }}
          />
          
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              Overall Rating
            </Typography>
            <Rating size="large" />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFeedbackDialog({ open: false, articleId: '' })}>
            Cancel
          </Button>
          <Button 
            variant="contained"
            onClick={() => {
              // Submit detailed feedback
              setFeedbackDialog({ open: false, articleId: '' });
              toast.success('Thank you for your detailed feedback!');
            }}
          >
            Submit Feedback
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );

  // Route rendering logic
  if (articleSlug) {
    return articleLoading ? (
      <Container><Skeleton variant="rectangular" height={400} /></Container>
    ) : (
      renderArticleView()
    );
  }

  if (categorySlug) {
    return renderCategoryView();
  }

  return renderHomePage();
};