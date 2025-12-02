# React Performance Optimization Patterns

## 1. React.memo - Prevent Unnecessary Re-renders

### When to Use
- Components that render often but props rarely change
- List items in large lists
- Pure components (same props = same output)

### Example: Transaction Card
```typescript
interface TransactionCardProps {
  transaction: Transaction;
  onSelect?: (id: string) => void;
}

// ❌ Without optimization
export function TransactionCard({ transaction, onSelect }: TransactionCardProps) {
  return (
    <Card onClick={() => onSelect?.(transaction.id)}>
      <Typography>{transaction.description}</Typography>
      <Typography>{formatCurrency(transaction.amount)}</Typography>
    </Card>
  );
}

// ✅ With React.memo
export const TransactionCard = React.memo(({ transaction, onSelect }: TransactionCardProps) => {
  return (
    <Card onClick={() => onSelect?.(transaction.id)}>
      <Typography>{transaction.description}</Typography>
      <Typography>{formatCurrency(transaction.amount)}</Typography>
    </Card>
  );
});

// ✅✅ With custom comparison (advanced)
export const TransactionCard = React.memo(
  ({ transaction, onSelect }: TransactionCardProps) => {
    // Component body...
  },
  (prevProps, nextProps) => {
    // Return true if props are equal (skip re-render)
    return (
      prevProps.transaction.id === nextProps.transaction.id &&
      prevProps.transaction.amount === nextProps.transaction.amount &&
      prevProps.transaction.status === nextProps.transaction.status
    );
  }
);
```

## 2. useMemo - Expensive Computations

### When to Use
- Filtering/sorting large arrays
- Complex calculations
- Creating objects/arrays passed as props

### Example: Transaction Filtering
```typescript
function TransactionList() {
  const transactions = useAppSelector(state => state.transactions.list);
  const [filter, setFilter] = useState('all');

  // ❌ Without optimization - recalculates every render
  const filteredTransactions = transactions.filter(t =>
    filter === 'all' ? true : t.type === filter
  );

  // ✅ With useMemo - only recalculates when dependencies change
  const filteredTransactions = useMemo(() => {
    return transactions.filter(t =>
      filter === 'all' ? true : t.type === filter
    );
  }, [transactions, filter]);

  // ✅ Complex stats calculation
  const stats = useMemo(() => {
    return {
      total: filteredTransactions.reduce((sum, t) => sum + t.amount, 0),
      average: filteredTransactions.length
        ? filteredTransactions.reduce((sum, t) => sum + t.amount, 0) / filteredTransactions.length
        : 0,
      byCategory: groupByCategory(filteredTransactions),
    };
  }, [filteredTransactions]);

  return (
    <div>
      <TransactionStats stats={stats} />
      {filteredTransactions.map(t => (
        <TransactionCard key={t.id} transaction={t} />
      ))}
    </div>
  );
}
```

## 3. useCallback - Stable Function References

### When to Use
- Functions passed as props to memoized components
- Functions used in dependency arrays
- Event handlers passed to child components

### Example: List with Actions
```typescript
function TransactionList() {
  const dispatch = useAppDispatch();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // ❌ Without useCallback - new function every render
  const handleSelect = (id: string) => {
    setSelectedId(id);
    dispatch(loadTransactionDetails(id));
  };

  // ✅ With useCallback - same function reference
  const handleSelect = useCallback((id: string) => {
    setSelectedId(id);
    dispatch(loadTransactionDetails(id));
  }, [dispatch]); // Only recreate if dispatch changes (never)

  const handleDelete = useCallback(async (id: string) => {
    await dispatch(deleteTransaction(id));
    setSelectedId(null);
  }, [dispatch]);

  return (
    <div>
      {transactions.map(t => (
        <TransactionCard
          key={t.id}
          transaction={t}
          onSelect={handleSelect}
          onDelete={handleDelete}
        />
      ))}
    </div>
  );
}
```

## 4. List Virtualization - Large Lists

### When to Use
- Lists with 100+ items
- Infinite scroll
- Tables with many rows

### Example: Transaction History
```typescript
import { FixedSizeList } from 'react-window';

function TransactionHistory() {
  const transactions = useAppSelector(state => state.transactions.list);

  // Row renderer
  const Row = useCallback(({ index, style }) => {
    const transaction = transactions[index];
    return (
      <div style={style}>
        <TransactionCard transaction={transaction} />
      </div>
    );
  }, [transactions]);

  return (
    <FixedSizeList
      height={600}
      itemCount={transactions.length}
      itemSize={80}
      width="100%"
    >
      {Row}
    </FixedSizeList>
  );
}
```

### Install
```bash
npm install react-window
```

## 5. Code Splitting by Component

### Heavy Components
```typescript
// Analytics with charts
const AnalyticsChart = lazy(() => import('./components/AnalyticsChart'));

function Dashboard() {
  const [showAnalytics, setShowAnalytics] = useState(false);

  return (
    <div>
      <Button onClick={() => setShowAnalytics(true)}>
        Show Analytics
      </Button>

      {showAnalytics && (
        <Suspense fallback={<Skeleton variant="rectangular" height={400} />}>
          <AnalyticsChart />
        </Suspense>
      )}
    </div>
  );
}
```

## 6. Debouncing and Throttling

### Search Input
```typescript
import { debounce } from '../utils/helpers';

function SearchBar() {
  const dispatch = useAppDispatch();

  // ❌ Without debouncing - API call on every keystroke
  const handleSearch = (query: string) => {
    dispatch(searchTransactions(query));
  };

  // ✅ With debouncing - API call after user stops typing
  const debouncedSearch = useMemo(
    () => debounce((query: string) => {
      dispatch(searchTransactions(query));
    }, 300),
    [dispatch]
  );

  return (
    <TextField
      onChange={(e) => debouncedSearch(e.target.value)}
      placeholder="Search transactions..."
    />
  );
}
```

### Scroll Event
```typescript
import { throttle } from '../utils/helpers';

function InfiniteScroll() {
  const handleScroll = useMemo(
    () => throttle(() => {
      if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 500) {
        loadMore();
      }
    }, 200),
    []
  );

  useEffect(() => {
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  return <div>{/* content */}</div>;
}
```

## 7. Image Optimization

### Lazy Loading Images
```typescript
function TransactionImage({ url, alt }: { url: string; alt: string }) {
  return (
    <img
      src={url}
      alt={alt}
      loading="lazy" // Native lazy loading
      style={{ objectFit: 'cover' }}
    />
  );
}
```

### Progressive Image Loading
```typescript
function ProgressiveImage({ src, placeholder }: { src: string; placeholder: string }) {
  const [imgSrc, setImgSrc] = useState(placeholder);

  useEffect(() => {
    const img = new Image();
    img.src = src;
    img.onload = () => setImgSrc(src);
  }, [src]);

  return (
    <img
      src={imgSrc}
      style={{
        filter: imgSrc === placeholder ? 'blur(10px)' : 'none',
        transition: 'filter 0.3s',
      }}
    />
  );
}
```

## 8. Redux Selector Optimization

### Reselect for Derived State
```typescript
import { createSelector } from '@reduxjs/toolkit';

// ❌ Without memoization - recalculates every time
const selectFilteredTransactions = (state: RootState) => {
  return state.transactions.list.filter(t => t.status === 'completed');
};

// ✅ With memoization - only recalculates when transactions change
const selectFilteredTransactions = createSelector(
  [(state: RootState) => state.transactions.list],
  (transactions) => transactions.filter(t => t.status === 'completed')
);

// ✅✅ Multiple selectors
const selectCompletedTransactions = createSelector(
  [(state: RootState) => state.transactions.list],
  (transactions) => transactions.filter(t => t.status === 'completed')
);

const selectTotalAmount = createSelector(
  [selectCompletedTransactions],
  (transactions) => transactions.reduce((sum, t) => sum + t.amount, 0)
);
```

## 9. Avoid Inline Object/Array Creation

### Props
```typescript
// ❌ New object every render
<TransactionCard
  style={{ marginBottom: 16 }}
  filters={{ status: 'completed', type: 'payment' }}
/>

// ✅ Stable reference
const cardStyle = { marginBottom: 16 };
const filters = useMemo(() => ({ status: 'completed', type: 'payment' }), []);

<TransactionCard style={cardStyle} filters={filters} />
```

## 10. Performance Monitoring

### Custom Hook
```typescript
function useRenderCount(componentName: string) {
  const renderCount = useRef(0);

  useEffect(() => {
    renderCount.current += 1;
    console.log(`${componentName} rendered ${renderCount.current} times`);
  });
}

function MyComponent() {
  useRenderCount('MyComponent');
  // ...
}
```

### React DevTools Profiler
```typescript
import { Profiler } from 'react';

function onRenderCallback(
  id, // the "id" prop of the Profiler tree that has just committed
  phase, // either "mount" (first render) or "update" (re-render)
  actualDuration, // time spent rendering the committed update
  baseDuration, // estimated time to render the entire subtree without memoization
  startTime, // when React began rendering this update
  commitTime, // when React committed this update
  interactions // the Set of interactions belonging to this update
) {
  console.log(`${id} took ${actualDuration}ms to render`);
}

<Profiler id="TransactionList" onRender={onRenderCallback}>
  <TransactionList />
</Profiler>
```

## 11. Summary Checklist

### Always Do
- ✅ Lazy load routes
- ✅ Use React.memo for list items
- ✅ Virtualize lists > 100 items
- ✅ Debounce search/filter inputs
- ✅ Lazy load images
- ✅ Use memoized selectors

### Consider
- 🤔 useMemo for expensive calculations
- 🤔 useCallback for functions passed to children
- 🤔 Code split heavy components
- 🤔 Progressive image loading

### Avoid
- ❌ Premature optimization
- ❌ Over-memoization (adds complexity)
- ❌ Inline object/array creation in props
- ❌ Unnecessary state updates

## 12. Measuring Impact

### Before Optimization
```typescript
// Example: Transaction list with 1000 items
// Render time: 450ms
// Re-render on parent update: 450ms
```

### After Optimization
```typescript
// React.memo + virtualization
// Initial render: 80ms (only visible items)
// Re-render on parent update: 0ms (memoized)
// Scroll performance: 60 FPS
```

**Result: 82% faster rendering**
