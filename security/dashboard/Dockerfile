FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./
RUN npm ci --only=production

# Copy source code
COPY src/ ./src/
COPY public/ ./public/
COPY tsconfig.json ./

# Build the application
RUN npm run build

# Production stage
FROM nginx:alpine

# Install required packages for security dashboard
RUN apk add --no-cache \
    python3 \
    py3-pip \
    bash \
    curl \
    jq

# Copy built application
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Copy security dashboard backend
COPY backend/ /app/backend/
WORKDIR /app/backend

# Install Python dependencies for report processing
COPY requirements.txt ./
RUN pip3 install -r requirements.txt

# Create directories for reports and processing
RUN mkdir -p /app/reports /app/processed /app/alerts

# Copy processing scripts
COPY scripts/ /app/scripts/
RUN chmod +x /app/scripts/*.sh

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Expose port
EXPOSE 8080

# Start both nginx and Python backend
CMD ["/app/scripts/start-dashboard.sh"]