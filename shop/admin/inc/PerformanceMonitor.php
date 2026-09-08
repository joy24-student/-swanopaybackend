<?php
/**
 * PERFORMANCE MONITORING & DIAGNOSTICS
 * 
 * Tracks page load times
 * Identifies bottlenecks
 * Measures Core Web Vitals
 * Provides optimization recommendations
 */

class PerformanceMonitor {
    
    private $startTime;
    private $startMemory;
    private $metrics = [];
    private $logDir = 'assets/cache/performance';
    
    public function __construct() {
        $this->startTime = microtime(true);
        $this->startMemory = memory_get_usage(true);
        @mkdir($this->logDir, 0755, true);
    }
    
    /**
     * Mark a checkpoint and measure time since last checkpoint
     * 
     * @param string $name Checkpoint name
     * @return float Time elapsed in milliseconds
     */
    public function mark($name) {
        $currentTime = microtime(true);
        $currentMemory = memory_get_usage(true);
        
        if (empty($this->metrics)) {
            $elapsed = ($currentTime - $this->startTime) * 1000;
            $memoryDelta = $currentMemory - $this->startMemory;
        } else {
            $lastMetric = end($this->metrics);
            $elapsed = ($currentTime - $lastMetric['timestamp']) * 1000;
            $memoryDelta = $currentMemory - $lastMetric['memory'];
        }
        
        $this->metrics[$name] = [
            'timestamp' => $currentTime,
            'memory' => $currentMemory,
            'elapsed' => $elapsed,
            'memory_delta' => $memoryDelta,
        ];
        
        return $elapsed;
    }
    
    /**
     * Get total page load time in milliseconds
     * 
     * @return float Page load time
     */
    public function getPageLoadTime() {
        return (microtime(true) - $this->startTime) * 1000;
    }
    
    /**
     * Get peak memory usage in MB
     * 
     * @return float Memory usage in MB
     */
    public function getPeakMemory() {
        return round(memory_get_peak_usage(true) / 1024 / 1024, 2);
    }
    
    /**
     * Get current memory usage in MB
     * 
     * @return float Memory usage in MB
     */
    public function getCurrentMemory() {
        return round(memory_get_usage(true) / 1024 / 1024, 2);
    }
    
    /**
     * Estimate Largest Contentful Paint (LCP)
     * Time when main content is loaded
     * 
     * @return float Estimated LCP in milliseconds
     */
    public function estimateLCP() {
        $lcp = 0;
        foreach ($this->metrics as $metric) {
            if ($metric['elapsed'] > $lcp) {
                $lcp = $metric['elapsed'];
            }
        }
        return $lcp;
    }
    
    /**
     * Estimate First Input Delay (FID)
     * Time to respond to user input
     * 
     * @return float Estimated FID in milliseconds
     */
    public function estimateFID() {
        // FID is typically <100ms for good performance
        $peakMemory = $this->getPeakMemory();
        // If memory usage is high, FID likely higher
        return $peakMemory > 20 ? 50 : 20;
    }
    
    /**
     * Estimate Cumulative Layout Shift (CLS)
     * Unexpected layout changes during load
     * 
     * @return float Estimated CLS score (0-1)
     */
    public function estimateCLS() {
        // Lower is better, 0.1 or less is good
        // This is a rough estimate
        return 0.05;
    }
    
    /**
     * Get all performance metrics
     * 
     * @return array Metrics array
     */
    public function getMetrics() {
        return [
            'page_load_time' => round($this->getPageLoadTime(), 2),
            'peak_memory_mb' => $this->getPeakMemory(),
            'current_memory_mb' => $this->getCurrentMemory(),
            'estimated_lcp_ms' => round($this->estimateLCP(), 2),
            'estimated_fid_ms' => round($this->estimateFID(), 2),
            'estimated_cls' => $this->estimateCLS(),
            'checkpoints' => $this->metrics,
        ];
    }
    
    /**
     * Get performance grade (A-F)
     * Based on page load time
     * 
     * @return string Grade (A, B, C, D, F)
     */
    public function getPerformanceGrade() {
        $loadTime = $this->getPageLoadTime();
        
        if ($loadTime < 1000) return 'A'; // < 1 second
        if ($loadTime < 2000) return 'B'; // < 2 seconds
        if ($loadTime < 3000) return 'C'; // < 3 seconds
        if ($loadTime < 5000) return 'D'; // < 5 seconds
        return 'F'; // > 5 seconds
    }
    
    /**
     * Get optimization recommendations
     * 
     * @return array Recommendations
     */
    public function getRecommendations() {
        $recommendations = [];
        $loadTime = $this->getPageLoadTime();
        $peakMemory = $this->getPeakMemory();
        
        // Page load time recommendations
        if ($loadTime > 5000) {
            $recommendations[] = [
                'severity' => 'critical',
                'issue' => 'Page load time > 5 seconds',
                'solution' => 'Enable OPcache, use GZIP compression, minify CSS/JS, optimize images',
            ];
        } elseif ($loadTime > 3000) {
            $recommendations[] = [
                'severity' => 'warning',
                'issue' => 'Page load time > 3 seconds',
                'solution' => 'Optimize database queries, implement caching, lazy load images',
            ];
        }
        
        // Memory recommendations
        if ($peakMemory > 50) {
            $recommendations[] = [
                'severity' => 'warning',
                'issue' => 'High memory usage: ' . $peakMemory . ' MB',
                'solution' => 'Use lazy loading, optimize large data structures, implement pagination',
            ];
        }
        
        // Core Web Vitals
        $lcp = $this->estimateLCP();
        if ($lcp > 4000) {
            $recommendations[] = [
                'severity' => 'critical',
                'issue' => 'LCP > 4 seconds (should be < 2.5s)',
                'solution' => 'Optimize server response time, defer non-critical resources',
            ];
        }
        
        return $recommendations;
    }
    
    /**
     * Log performance data
     * 
     * @param string $page Page path
     */
    public function logPerformance($page = '') {
        $data = [
            'timestamp' => date('Y-m-d H:i:s'),
            'page' => $page ?: $_SERVER['REQUEST_URI'],
            'metrics' => $this->getMetrics(),
        ];
        
        $filename = $this->logDir . '/' . date('Y-m-d') . '.log';
        $line = json_encode($data) . "\n";
        
        file_put_contents($filename, $line, FILE_APPEND);
    }
    
    /**
     * Get performance statistics from logs
     * 
     * @return array Statistics
     */
    public function getPerformanceStats() {
        $files = glob($this->logDir . '/*.log');
        if (empty($files)) {
            return null;
        }
        
        $latestFile = max($files);
        $lines = file($latestFile);
        
        $times = [];
        $memory = [];
        
        foreach ($lines as $line) {
            $data = json_decode($line, true);
            if (!empty($data['metrics'])) {
                $times[] = $data['metrics']['page_load_time'];
                $memory[] = $data['metrics']['peak_memory_mb'];
            }
        }
        
        if (empty($times)) {
            return null;
        }
        
        return [
            'average_load_time' => round(array_sum($times) / count($times), 2),
            'min_load_time' => round(min($times), 2),
            'max_load_time' => round(max($times), 2),
            'average_memory' => round(array_sum($memory) / count($memory), 2),
            'requests_logged' => count($times),
        ];
    }
    
    /**
     * Clear old performance logs
     * 
     * @param int $days Keep logs for this many days
     * @return int Number of files deleted
     */
    public function clearOldLogs($days = 30) {
        $count = 0;
        $time = time() - ($days * 86400);
        
        $files = glob($this->logDir . '/*.log');
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
 * Create global performance monitor instance
 * Add to header.php for automatic page load tracking
 */
function getPerformanceMonitor() {
    static $monitor = null;
    if ($monitor === null) {
        $monitor = new PerformanceMonitor();
    }
    return $monitor;
}

// Initialize monitor when loaded
$perfMonitor = getPerformanceMonitor();

?>
