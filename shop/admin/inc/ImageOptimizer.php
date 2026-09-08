<?php
/**
 * IMAGE OPTIMIZATION HANDLER
 * 
 * Handles image compression, WebP conversion, lazy loading, and responsive images
 * Automatically converts images to WebP format with fallback
 * Implements lazy loading for better performance
 */

class ImageOptimizer {
    
    private $uploadDir;
    private $webpDir;
    private $cacheDir;
    private $supportedFormats = ['jpg', 'jpeg', 'png', 'gif'];
    
    public function __construct($uploadDir = 'assets/products', $webpDir = 'assets/products/webp') {
        $this->uploadDir = $uploadDir;
        $this->webpDir = $webpDir;
        $this->cacheDir = 'assets/cache/images';
        
        // Create directories if they don't exist
        @mkdir($this->webpDir, 0755, true);
        @mkdir($this->cacheDir, 0755, true);
    }
    
    /**
     * Generate responsive image HTML with lazy loading
     * 
     * @param string $imagePath Path to image
     * @param string $alt Alt text for image
     * @param int $width Optional width
     * @param int $height Optional height
     * @return string HTML img tag
     */
    public function generateResponsiveImage($imagePath, $alt = '', $width = null, $height = null) {
        $imagePath = ltrim($imagePath, '/');
        $fileName = basename($imagePath);
        
        // Check if WebP version exists
        $webpPath = $this->webpDir . '/' . pathinfo($fileName, PATHINFO_FILENAME) . '.webp';
        $hasWebp = file_exists($webpPath);
        
        $widthAttr = $width ? " width=\"$width\"" : "";
        $heightAttr = $height ? " height=\"$height\"" : "";
        
        $html = '<picture>';
        
        // WebP source for modern browsers
        if ($hasWebp) {
            $html .= '<source srcset="' . htmlspecialchars($webpPath) . '" type="image/webp">';
        }
        
        // Fallback for older browsers
        $html .= '<img src="' . htmlspecialchars($imagePath) . '" ';
        $html .= 'alt="' . htmlspecialchars($alt) . '" ';
        $html .= 'loading="lazy" ';
        $html .= 'decoding="async"';
        $html .= $widthAttr;
        $html .= $heightAttr;
        $html .= ' class="responsive-image">';
        
        $html .= '</picture>';
        
        return $html;
    }
    
    /**
     * Generate srcset attribute for responsive images
     * Creates multiple size versions
     * 
     * @param string $imagePath Path to image
     * @param array $sizes Array of sizes [320, 640, 960, 1280]
     * @return string srcset attribute
     */
    public function generateSrcSet($imagePath, $sizes = [320, 640, 960, 1280]) {
        $fileName = basename($imagePath);
        $fileNameNoExt = pathinfo($fileName, PATHINFO_FILENAME);
        $ext = pathinfo($fileName, PATHINFO_EXTENSION);
        
        $srcsets = [];
        
        foreach ($sizes as $size) {
            $resizedFile = $fileNameNoExt . '-' . $size . '.' . $ext;
            $srcsets[] = htmlspecialchars($resizedFile) . ' ' . $size . 'w';
        }
        
        return implode(', ', $srcsets);
    }
    
    /**
     * Compress image file
     * Uses native PHP functions or ImageMagick if available
     * 
     * @param string $filePath Path to image file
     * @param int $quality Compression quality (1-100)
     * @return bool Success
     */
    public function compressImage($filePath, $quality = 85) {
        if (!file_exists($filePath)) {
            return false;
        }
        
        $ext = strtolower(pathinfo($filePath, PATHINFO_EXTENSION));
        
        // Use GD library for compression
        if (!extension_loaded('gd')) {
            return false;
        }
        
        try {
            switch ($ext) {
                case 'jpg':
                case 'jpeg':
                    $image = imagecreatefromjpeg($filePath);
                    if ($image) {
                        imagejpeg($image, $filePath, $quality);
                        imagedestroy($image);
                        return true;
                    }
                    break;
                    
                case 'png':
                    $image = imagecreatefrompng($filePath);
                    if ($image) {
                        // PNG compression level 1-9
                        $pngQuality = round((100 - $quality) / 11.111111);
                        imagepng($image, $filePath, $pngQuality);
                        imagedestroy($image);
                        return true;
                    }
                    break;
                    
                case 'gif':
                    // GIF compression less effective, try to optimize
                    $image = imagecreatefromgif($filePath);
                    if ($image) {
                        imagegif($image, $filePath);
                        imagedestroy($image);
                        return true;
                    }
                    break;
            }
        } catch (Exception $e) {
            error_log("Image compression error: " . $e->getMessage());
            return false;
        }
        
        return false;
    }
    
    /**
     * Convert image to WebP format
     * Modern format with better compression
     * 
     * @param string $sourcePath Source image path
     * @param string $destPath Destination WebP path
     * @param int $quality Compression quality (1-100)
     * @return bool Success
     */
    public function convertToWebP($sourcePath, $destPath, $quality = 85) {
        if (!extension_loaded('gd')) {
            return false;
        }
        
        if (!function_exists('imagewebp')) {
            return false;
        }
        
        try {
            $ext = strtolower(pathinfo($sourcePath, PATHINFO_EXTENSION));
            
            $image = null;
            switch ($ext) {
                case 'jpg':
                case 'jpeg':
                    $image = imagecreatefromjpeg($sourcePath);
                    break;
                case 'png':
                    $image = imagecreatefrompng($sourcePath);
                    break;
                case 'gif':
                    $image = imagecreatefromgif($sourcePath);
                    break;
            }
            
            if ($image) {
                imagewebp($image, $destPath, $quality);
                imagedestroy($image);
                return true;
            }
        } catch (Exception $e) {
            error_log("WebP conversion error: " . $e->getMessage());
        }
        
        return false;
    }
    
    /**
     * Resize image to specific dimensions
     * Maintains aspect ratio
     * 
     * @param string $sourcePath Source image path
     * @param string $destPath Destination path
     * @param int $maxWidth Maximum width
     * @param int $maxHeight Maximum height
     * @return bool Success
     */
    public function resizeImage($sourcePath, $destPath, $maxWidth, $maxHeight) {
        if (!extension_loaded('gd')) {
            return false;
        }
        
        try {
            list($origWidth, $origHeight) = getimagesize($sourcePath);
            
            // Calculate new dimensions maintaining aspect ratio
            $ratio = min($maxWidth / $origWidth, $maxHeight / $origHeight);
            
            $newWidth = round($origWidth * $ratio);
            $newHeight = round($origHeight * $ratio);
            
            $ext = strtolower(pathinfo($sourcePath, PATHINFO_EXTENSION));
            
            $image = null;
            switch ($ext) {
                case 'jpg':
                case 'jpeg':
                    $image = imagecreatefromjpeg($sourcePath);
                    break;
                case 'png':
                    $image = imagecreatefrompng($sourcePath);
                    break;
                case 'gif':
                    $image = imagecreatefromgif($sourcePath);
                    break;
            }
            
            if (!$image) {
                return false;
            }
            
            $resizedImage = imagecreatetruecolor($newWidth, $newHeight);
            
            // Preserve transparency for PNG and GIF
            if ($ext === 'png' || $ext === 'gif') {
                imagecolortransparent($resizedImage, imagecolorallocatealpha($resizedImage, 0, 0, 0, 127));
                imagealphablending($resizedImage, false);
                imagesavealpha($resizedImage, true);
            }
            
            imagecopyresampled($resizedImage, $image, 0, 0, 0, 0, $newWidth, $newHeight, $origWidth, $origHeight);
            
            // Save based on format
            switch ($ext) {
                case 'jpg':
                case 'jpeg':
                    imagejpeg($resizedImage, $destPath, 85);
                    break;
                case 'png':
                    imagepng($resizedImage, $destPath, 6);
                    break;
                case 'gif':
                    imagegif($resizedImage, $destPath);
                    break;
            }
            
            imagedestroy($image);
            imagedestroy($resizedImage);
            
            return true;
        } catch (Exception $e) {
            error_log("Image resize error: " . $e->getMessage());
        }
        
        return false;
    }
    
    /**
     * Generate thumbnail image
     * 
     * @param string $sourcePath Source image path
     * @param int $thumbWidth Thumbnail width
     * @return string Path to thumbnail
     */
    public function generateThumbnail($sourcePath, $thumbWidth = 300) {
        if (!file_exists($sourcePath)) {
            return $sourcePath;
        }
        
        $fileName = basename($sourcePath);
        $fileNameNoExt = pathinfo($fileName, PATHINFO_FILENAME);
        $ext = pathinfo($fileName, PATHINFO_EXTENSION);
        
        $thumbName = $fileNameNoExt . '-thumb-' . $thumbWidth . '.' . $ext;
        $thumbPath = $this->cacheDir . '/' . $thumbName;
        
        // Return existing thumbnail
        if (file_exists($thumbPath)) {
            return $thumbPath;
        }
        
        // Create thumbnail
        if ($this->resizeImage($sourcePath, $thumbPath, $thumbWidth, $thumbWidth)) {
            return $thumbPath;
        }
        
        return $sourcePath;
    }
    
    /**
     * Get image file size in KB
     * 
     * @param string $filePath Path to image
     * @return float File size in KB
     */
    public function getFileSize($filePath) {
        if (file_exists($filePath)) {
            return round(filesize($filePath) / 1024, 2);
        }
        return 0;
    }
    
    /**
     * Generate optimized picture element with multiple formats
     * 
     * @param string $imagePath Path to image
     * @param string $alt Alt text
     * @param array $sizes Responsive sizes
     * @return string HTML picture element
     */
    public function generateOptimizedPicture($imagePath, $alt = '', $sizes = []) {
        $ext = strtolower(pathinfo($imagePath, PATHINFO_EXTENSION));
        $fileNameNoExt = pathinfo($imagePath, PATHINFO_FILENAME);
        $dir = dirname($imagePath);
        
        $html = '<picture>';
        
        // WebP source
        $webpPath = $dir . '/' . $fileNameNoExt . '.webp';
        if (file_exists($webpPath)) {
            $html .= '<source srcset="' . htmlspecialchars($webpPath) . '" type="image/webp">';
        }
        
        // AVIF source (next-gen format)
        $avifPath = $dir . '/' . $fileNameNoExt . '.avif';
        if (file_exists($avifPath)) {
            $html .= '<source srcset="' . htmlspecialchars($avifPath) . '" type="image/avif">';
        }
        
        // Fallback
        $html .= '<img src="' . htmlspecialchars($imagePath) . '" ';
        $html .= 'alt="' . htmlspecialchars($alt) . '" ';
        $html .= 'loading="lazy" decoding="async"';
        if (!empty($sizes)) {
            $html .= ' sizes="' . htmlspecialchars(implode(', ', $sizes)) . '"';
        }
        $html .= ' class="responsive-image">';
        
        $html .= '</picture>';
        
        return $html;
    }
}

/**
 * Helper function to get image optimizer instance
 */
function getImageOptimizer() {
    static $optimizer = null;
    if ($optimizer === null) {
        $optimizer = new ImageOptimizer();
    }
    return $optimizer;
}

?>
