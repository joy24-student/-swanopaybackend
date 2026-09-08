<?php
/**
 * DATABASE OPTIMIZATION & QUERY CACHING
 * 
 * Caches database queries
 * Provides query optimization suggestions
 * Implements lazy loading
 * Reduces database load significantly
 */

class DatabaseOptimizer {
    
    private $pdo;
    private $cacheDir = 'assets/cache/database';
    private $cacheDuration = 3600; // 1 hour default
    
    public function __construct(&$pdo) {
        $this->pdo = $pdo;
        @mkdir($this->cacheDir, 0755, true);
    }
    
    /**
     * Cache a database query result
     * Returns cached result if available, otherwise executes and caches
     * 
     * @param string $query SQL query
     * @param array $params Query parameters
     * @param int $duration Cache duration in seconds
     * @param string $cacheKey Optional custom cache key
     * @return array Query results
     */
    public function getCachedQuery($query, $params = [], $duration = null, $cacheKey = null) {
        if ($duration === null) {
            $duration = $this->cacheDuration;
        }
        
        // Generate cache key
        if ($cacheKey === null) {
            $cacheKey = md5($query . serialize($params));
        }
        
        $cacheFile = $this->cacheDir . '/' . $cacheKey . '.cache';
        
        // Check if cache exists and is fresh
        if (file_exists($cacheFile)) {
            $cacheAge = time() - filemtime($cacheFile);
            if ($cacheAge < $duration) {
                $cached = unserialize(file_get_contents($cacheFile));
                if ($cached !== false) {
                    return $cached;
                }
            }
        }
        
        // Execute query
        try {
            $statement = $this->pdo->prepare($query);
            $statement->execute($params);
            $results = $statement->fetchAll(PDO::FETCH_ASSOC);
            
            // Cache results
            file_put_contents($cacheFile, serialize($results));
            
            return $results;
        } catch (Exception $e) {
            error_log("Database query error: " . $e->getMessage());
            return [];
        }
    }
    
    /**
     * Get a single cached query result
     * 
     * @param string $query SQL query
     * @param array $params Query parameters
     * @param int $duration Cache duration in seconds
     * @return array Single row or empty array
     */
    public function getCachedQuerySingle($query, $params = [], $duration = null) {
        $results = $this->getCachedQuery($query, $params, $duration);
        return !empty($results) ? $results[0] : [];
    }
    
    /**
     * Invalidate cache for a query
     * 
     * @param string $query SQL query or cache key
     * @param array $params Query parameters (if using query instead of key)
     */
    public function invalidateCache($query, $params = []) {
        // If it looks like a cache key (alphanumeric), use directly
        if (preg_match('/^[a-f0-9]{32}$/', $query)) {
            $cacheKey = $query;
        } else {
            $cacheKey = md5($query . serialize($params));
        }
        
        $cacheFile = $this->cacheDir . '/' . $cacheKey . '.cache';
        if (file_exists($cacheFile)) {
            @unlink($cacheFile);
        }
    }
    
    /**
     * Clear all database cache
     * Use when updating products/categories
     */
    public function clearAllCache() {
        $files = glob($this->cacheDir . '/*.cache');
        foreach ($files as $file) {
            @unlink($file);
        }
    }
    
    /**
     * Implement lazy loading for large queries
     * Returns iterator for memory-efficient processing
     * 
     * @param string $query SQL query
     * @param array $params Query parameters
     * @param int $batchSize Number of rows per batch
     * @return Generator Results iterator
     */
    public function lazyLoadQuery($query, $params = [], $batchSize = 100) {
        try {
            $statement = $this->pdo->prepare($query);
            $statement->execute($params);
            
            $rows = [];
            while ($row = $statement->fetch(PDO::FETCH_ASSOC)) {
                $rows[] = $row;
                
                if (count($rows) >= $batchSize) {
                    yield $rows;
                    $rows = [];
                }
            }
            
            // Yield remaining rows
            if (!empty($rows)) {
                yield $rows;
            }
        } catch (Exception $e) {
            error_log("Lazy load query error: " . $e->getMessage());
        }
    }
    
    /**
     * Get query execution plan (EXPLAIN)
     * Helps identify slow queries
     * 
     * @param string $query SQL SELECT query
     * @return array Execution plan
     */
    public function explainQuery($query) {
        try {
            $explainQuery = 'EXPLAIN ' . $query;
            $statement = $this->pdo->prepare($explainQuery);
            $statement->execute();
            return $statement->fetchAll(PDO::FETCH_ASSOC);
        } catch (Exception $e) {
            return [];
        }
    }
    
    /**
     * Suggest database indexes for common queries
     * 
     * @return array Suggested indexes
     */
    public function suggestIndexes() {
        $suggestions = [
            'tbl_product' => [
                'p_is_active' => 'For filtering active products',
                'ecat_id' => 'For category filtering',
                'p_current_price' => 'For price-based sorting',
                'p_total_view' => 'For popularity sorting',
                'p_is_featured' => 'For featured products',
                'created_at' => 'For date-based sorting',
            ],
            'tbl_customer' => [
                'cust_email' => 'For login/email lookups',
                'cust_status' => 'For active customer filtering',
            ],
            'tbl_order' => [
                'cust_id' => 'For customer order lookups',
                'order_date' => 'For date-based filtering',
                'order_status' => 'For status filtering',
            ],
            'tbl_review' => [
                'product_id' => 'For product reviews',
                'status' => 'For approved reviews filtering',
                'created_at' => 'For recent reviews',
            ],
        ];
        
        return $suggestions;
    }
    
    /**
     * Check if index exists
     * 
     * @param string $table Table name
     * @param string $column Column name
     * @return bool Index exists
     */
    public function indexExists($table, $column) {
        try {
            $statement = $this->pdo->prepare("SHOW INDEX FROM $table WHERE Column_name = ?");
            $statement->execute([$column]);
            return $statement->rowCount() > 0;
        } catch (Exception $e) {
            return false;
        }
    }
    
    /**
     * Create database index
     * 
     * @param string $table Table name
     * @param string $column Column name
     * @param string $indexName Optional index name
     * @return bool Success
     */
    public function createIndex($table, $column, $indexName = null) {
        if ($this->indexExists($table, $column)) {
            return true;
        }
        
        if ($indexName === null) {
            $indexName = 'idx_' . $table . '_' . $column;
        }
        
        try {
            $sql = "CREATE INDEX $indexName ON $table ($column)";
            $this->pdo->exec($sql);
            return true;
        } catch (Exception $e) {
            error_log("Index creation error: " . $e->getMessage());
            return false;
        }
    }
    
    /**
     * Get database statistics
     * Table sizes, row counts, etc.
     * 
     * @return array Database stats
     */
    public function getDatabaseStats() {
        try {
            $stats = [];
            
            // Get table information
            $statement = $this->pdo->query("SELECT table_name, table_rows, data_length FROM information_schema.tables WHERE table_schema = DATABASE()");
            $tables = $statement->fetchAll(PDO::FETCH_ASSOC);
            
            foreach ($tables as $table) {
                $stats[$table['table_name']] = [
                    'rows' => $table['table_rows'],
                    'size_mb' => round($table['data_length'] / 1024 / 1024, 2),
                ];
            }
            
            return $stats;
        } catch (Exception $e) {
            return [];
        }
    }
    
    /**
     * Optimize table
     * Reclaims space and improves performance
     * 
     * @param string $table Table name
     * @return bool Success
     */
    public function optimizeTable($table) {
        try {
            $this->pdo->exec("OPTIMIZE TABLE $table");
            return true;
        } catch (Exception $e) {
            error_log("Table optimization error: " . $e->getMessage());
            return false;
        }
    }
    
    /**
     * Set cache duration for subsequent queries
     * 
     * @param int $seconds Cache duration in seconds
     */
    public function setCacheDuration($seconds) {
        $this->cacheDuration = $seconds;
    }
    
    /**
     * Clean old cache files
     * 
     * @param int $maxAge Maximum age in seconds
     * @return int Number of files deleted
     */
    public function cleanCache($maxAge = 86400) {
        $count = 0;
        $time = time() - $maxAge;
        
        $files = glob($this->cacheDir . '/*.cache');
        foreach ($files as $file) {
            if (filemtime($file) < $time) {
                if (@unlink($file)) {
                    $count++;
                }
            }
        }
        
        return $count;
    }
}

/**
 * Helper function to get database optimizer instance
 */
function getDatabaseOptimizer($pdo = null) {
    static $optimizer = null;
    if ($optimizer === null && $pdo !== null) {
        $optimizer = new DatabaseOptimizer($pdo);
    }
    return $optimizer;
}

?>
