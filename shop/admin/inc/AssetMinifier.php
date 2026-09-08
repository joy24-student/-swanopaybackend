<?php
/**
 * ASSET MINIFICATION & BUNDLING SYSTEM
 * 
 * Minifies CSS and JavaScript
 * Combines multiple files into single bundle
 * Caches minified versions
 * Removes comments, whitespace, and unused code
 */

class AssetMinifier {
    
    private $cacheDir = 'assets/cache/minified';
    private $cssDir = 'assets/css';
    private $jsDir = 'assets/js';
    
    public function __construct() {
        @mkdir($this->cacheDir, 0755, true);
    }
    
    /**
     * Minify CSS content
     * Removes comments, whitespace, optimizes rules
     * 
     * @param string $css CSS content
     * @return string Minified CSS
     */
    public function minifyCSS($css) {
        // Remove comments
        $css = preg_replace('!/\*[^*]*\*+(?:[^/*][^*]*\*+)*/!', '', $css);
        
        // Remove tabs, line breaks
        $css = str_replace(["\r\n", "\r", "\n", "\t"], '', $css);
        
        // Remove spaces before and after some characters
        $css = preg_replace('/\s+/', ' ', $css);
        $css = preg_replace(['/ ([{:>;,]) /', '/ ([{:>;,])/'], '$1', $css);
        
        // Remove trailing semicolon in rules
        $css = str_replace(';}', '}', $css);
        
        // Remove last semicolon in rule
        $css = preg_replace('/;(?=\})/', '', $css);
        
        // Remove spaces around commas in selectors
        $css = preg_replace('/\s*,\s*/', ',', $css);
        
        // Minimize colors
        $css = $this->minimizeColors($css);
        
        // Remove empty rules
        $css = preg_replace('/[^}]+\{[^}]*\}/', '', $css);
        
        return trim($css);
    }
    
    /**
     * Minify JavaScript content
     * Removes comments, whitespace
     * 
     * @param string $js JavaScript content
     * @return string Minified JavaScript
     */
    public function minifyJS($js) {
        // Remove single-line comments
        $js = preg_replace('~//.*$~m', '', $js);
        
        // Remove multi-line comments
        $js = preg_replace('~/\*.*?\*/~s', '', $js);
        
        // Remove whitespace
        $js = preg_replace('~\s+~', ' ', $js);
        
        // Remove spaces around operators and punctuation
        $js = preg_replace('~\s*([{}();,:])\s*~', '$1', $js);
        $js = preg_replace('~\s*([+\-*/<>=!&|?])\s*~', '$1', $js);
        
        // Remove unnecessary semicolons
        $js = str_replace(';}', '}', $js);
        
        // Preserve required spaces after keywords
        $keywords = ['if', 'else', 'for', 'while', 'do', 'switch', 'case', 'return', 'function', 'var', 'let', 'const'];
        foreach ($keywords as $keyword) {
            $js = preg_replace('~' . $keyword . '(?!\w)~', $keyword . ' ', $js);
        }
        
        return trim($js);
    }
    
    /**
     * Minimize CSS color values
     * Converts #rrggbb to #rgb where possible
     * 
     * @param string $css CSS content
     * @return string Optimized CSS
     */
    private function minimizeColors($css) {
        // Convert #rrggbb to #rgb
        return preg_replace_callback(
            '/#([0-9a-f])\1([0-9a-f])\2([0-9a-f])\3(?![0-9a-f])/i',
            function($m) { return '#' . $m[1] . $m[2] . $m[3]; },
            $css
        );
    }
    
    /**
     * Bundle multiple CSS files into one
     * 
     * @param array $files Array of CSS file paths
     * @param string $bundleName Name for bundle (without .css)
     * @return string Path to bundled file
     */
    public function bundleCSS($files, $bundleName = 'bundle') {
        $bundlePath = $this->cacheDir . '/' . $bundleName . '.css';
        
        // Return cached bundle if exists and is newer
        if (file_exists($bundlePath)) {
            $bundleTime = filemtime($bundlePath);
            $needsRebuild = false;
            
            foreach ($files as $file) {
                if (!file_exists($file)) continue;
                if (filemtime($file) > $bundleTime) {
                    $needsRebuild = true;
                    break;
                }
            }
            
            if (!$needsRebuild) {
                return $bundlePath;
            }
        }
        
        // Build bundle
        $bundled = '';
        foreach ($files as $file) {
            if (!file_exists($file)) {
                continue;
            }
            
            $content = file_get_contents($file);
            
            // Fix relative URLs in CSS
            $dir = dirname($file);
            $content = preg_replace_callback(
                '/url\([\'"]?(?!(?:https?:|\/|data:))([^\)]+)[\'"]?\)/i',
                function($m) use ($dir) {
                    return 'url(' . htmlspecialchars(realpath($dir . '/' . $m[1])) . ')';
                },
                $content
            );
            
            $bundled .= $this->minifyCSS($content) . "\n";
        }
        
        file_put_contents($bundlePath, $bundled);
        return $bundlePath;
    }
    
    /**
     * Bundle multiple JavaScript files into one
     * 
     * @param array $files Array of JS file paths
     * @param string $bundleName Name for bundle (without .js)
     * @return string Path to bundled file
     */
    public function bundleJS($files, $bundleName = 'bundle') {
        $bundlePath = $this->cacheDir . '/' . $bundleName . '.js';
        
        // Return cached bundle if exists and is newer
        if (file_exists($bundlePath)) {
            $bundleTime = filemtime($bundlePath);
            $needsRebuild = false;
            
            foreach ($files as $file) {
                if (!file_exists($file)) continue;
                if (filemtime($file) > $bundleTime) {
                    $needsRebuild = true;
                    break;
                }
            }
            
            if (!$needsRebuild) {
                return $bundlePath;
            }
        }
        
        // Build bundle
        $bundled = "/* Bundled: " . date('Y-m-d H:i:s') . " */\n";
        foreach ($files as $file) {
            if (!file_exists($file)) {
                continue;
            }
            
            $content = file_get_contents($file);
            $bundled .= $this->minifyJS($content) . ";\n";
        }
        
        file_put_contents($bundlePath, $bundled);
        return $bundlePath;
    }
    
    /**
     * Get optimized CSS link tag
     * 
     * @param string $cssFile CSS file path
     * @param bool $minify Whether to minify
     * @return string HTML link tag
     */
    public function getCSSLink($cssFile, $minify = true) {
        $file = $cssFile;
        
        if ($minify && file_exists($cssFile)) {
            $hash = md5_file($cssFile);
            $minFile = $this->cacheDir . '/' . basename($cssFile, '.css') . '-' . substr($hash, 0, 8) . '.css';
            
            if (!file_exists($minFile)) {
                $content = file_get_contents($cssFile);
                file_put_contents($minFile, $this->minifyCSS($content));
            }
            
            $file = $minFile;
        }
        
        return '<link rel="stylesheet" href="' . htmlspecialchars($file) . '" media="screen">';
    }
    
    /**
     * Get optimized JavaScript script tag
     * 
     * @param string $jsFile JavaScript file path
     * @param bool $minify Whether to minify
     * @param bool $defer Whether to defer loading
     * @param bool $async Whether to load asynchronously
     * @return string HTML script tag
     */
    public function getJSScript($jsFile, $minify = true, $defer = true, $async = false) {
        $file = $jsFile;
        
        if ($minify && file_exists($jsFile)) {
            $hash = md5_file($jsFile);
            $minFile = $this->cacheDir . '/' . basename($jsFile, '.js') . '-' . substr($hash, 0, 8) . '.js';
            
            if (!file_exists($minFile)) {
                $content = file_get_contents($jsFile);
                file_put_contents($minFile, $this->minifyJS($content));
            }
            
            $file = $minFile;
        }
        
        $tag = '<script src="' . htmlspecialchars($file) . '"';
        if ($defer && !$async) $tag .= ' defer';
        if ($async) $tag .= ' async';
        $tag .= '></script>';
        
        return $tag;
    }
    
    /**
     * Calculate minification savings
     * 
     * @param string $original Original content
     * @param string $minified Minified content
     * @return array Stats array with sizes and percentage
     */
    public function getMinificationStats($original, $minified) {
        $originalSize = strlen($original);
        $minifiedSize = strlen($minified);
        $saved = $originalSize - $minifiedSize;
        $percentage = ($saved / $originalSize) * 100;
        
        return [
            'original_size' => $originalSize,
            'minified_size' => $minifiedSize,
            'saved_bytes' => $saved,
            'percentage' => round($percentage, 2),
        ];
    }
    
    /**
     * Clean cache directory
     * Removes old minified files
     * 
     * @param int $maxAge Maximum age in seconds (default: 30 days)
     * @return int Number of files deleted
     */
    public function cleanCache($maxAge = 2592000) {
        $count = 0;
        $time = time() - $maxAge;
        
        if (!is_dir($this->cacheDir)) {
            return 0;
        }
        
        $files = scandir($this->cacheDir);
        foreach ($files as $file) {
            if ($file === '.' || $file === '..') continue;
            
            $path = $this->cacheDir . '/' . $file;
            if (filemtime($path) < $time) {
                if (@unlink($path)) {
                    $count++;
                }
            }
        }
        
        return $count;
    }
}

/**
 * Helper function to get asset minifier instance
 */
function getAssetMinifier() {
    static $minifier = null;
    if ($minifier === null) {
        $minifier = new AssetMinifier();
    }
    return $minifier;
}

?>
