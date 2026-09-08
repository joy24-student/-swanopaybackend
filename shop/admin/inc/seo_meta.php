<?php
/**
 * SEO META TAGS & STRUCTURED DATA HELPER
 * Handles Open Graph tags, meta descriptions, JSON-LD schema markup
 */

/**
 * Generate SEO Meta Tags for Product Page
 */
function generateProductMetaTags($product, $baseUrl = '') {
    $html = "";
    
    // Product title (max 60 characters for Google)
    $title = htmlspecialchars($product['p_name'], ENT_QUOTES, 'UTF-8');
    $html .= '<meta property="og:title" content="' . $title . '" />' . "\n";
    
    // Product description (max 160 characters for Google)
    $description = htmlspecialchars(
        !empty($product['p_short_description']) ? 
        substr($product['p_short_description'], 0, 160) : 
        substr(strip_tags($product['p_description']), 0, 160),
        ENT_QUOTES, 'UTF-8'
    );
    $html .= '<meta name="description" content="' . $description . '" />' . "\n";
    $html .= '<meta property="og:description" content="' . $description . '" />' . "\n";
    
    // Product image
    if (!empty($product['p_featured_photo'])) {
        $imageUrl = $baseUrl . 'assets/products/' . htmlspecialchars($product['p_featured_photo'], ENT_QUOTES, 'UTF-8');
        $html .= '<meta property="og:image" content="' . $imageUrl . '" />' . "\n";
        $html .= '<meta property="og:image:width" content="800" />' . "\n";
        $html .= '<meta property="og:image:height" content="800" />' . "\n";
    }
    
    // Product price (for e-commerce structured data)
    $html .= '<meta property="product:price:amount" content="' . htmlspecialchars($product['p_current_price'], ENT_QUOTES) . '" />' . "\n";
    $html .= '<meta property="product:price:currency" content="USD" />' . "\n";
    
    // Open Graph type
    $html .= '<meta property="og:type" content="product" />' . "\n";
    
    // Keywords
    $keywords = htmlspecialchars($product['p_name'] . ', ' . ($product['p_condition'] ?? 'New'), ENT_QUOTES, 'UTF-8');
    $html .= '<meta name="keywords" content="' . $keywords . '" />' . "\n";
    
    // Twitter Card
    $html .= '<meta name="twitter:card" content="product" />' . "\n";
    $html .= '<meta name="twitter:title" content="' . $title . '" />' . "\n";
    $html .= '<meta name="twitter:description" content="' . $description . '" />' . "\n";
    if (!empty($product['p_featured_photo'])) {
        $imageUrl = $baseUrl . 'assets/products/' . htmlspecialchars($product['p_featured_photo'], ENT_QUOTES, 'UTF-8');
        $html .= '<meta name="twitter:image" content="' . $imageUrl . '" />' . "\n";
    }
    
    // Canonical URL (prevent duplicate content)
    $canonicalUrl = $baseUrl . 'product/' . generateUniqueSlug($product['p_name'], $product['p_id']);
    $html .= '<link rel="canonical" href="' . htmlspecialchars($canonicalUrl, ENT_QUOTES, 'UTF-8') . '" />' . "\n";
    
    return $html;
}

/**
 * Generate SEO Meta Tags for Category Page
 */
function generateCategoryMetaTags($category, $type = 'top-category', $baseUrl = '') {
    $html = "";
    
    $categoryName = htmlspecialchars($category['name'], ENT_QUOTES, 'UTF-8');
    
    // Category title
    $html .= '<meta property="og:title" content="' . $categoryName . ' | Shop Now" />' . "\n";
    
    // Category description
    $description = htmlspecialchars(
        'Explore our collection of ' . $categoryName . ' products. Free shipping on orders over $50.',
        ENT_QUOTES, 'UTF-8'
    );
    $html .= '<meta name="description" content="' . $description . '" />' . "\n";
    $html .= '<meta property="og:description" content="' . $description . '" />' . "\n";
    
    // Open Graph type
    $html .= '<meta property="og:type" content="website" />' . "\n";
    
    // Keywords
    $html .= '<meta name="keywords" content="' . $categoryName . ', shop, buy online, ecommerce" />' . "\n";
    
    // Canonical URL
    $canonicalUrl = $baseUrl . 'category/' . generateSlug($categoryName);
    $html .= '<link rel="canonical" href="' . htmlspecialchars($canonicalUrl, ENT_QUOTES, 'UTF-8') . '" />' . "\n";
    
    return $html;
}

/**
 * Generate JSON-LD Schema Markup for Product (Google Rich Snippets)
 */
function generateProductSchema($product, $baseUrl = '', $rating = 0, $reviewCount = 0) {
    $schema = array(
        "@context" => "https://schema.org/",
        "@type" => "Product",
        "name" => $product['p_name'],
        "description" => !empty($product['p_short_description']) ? 
                         $product['p_short_description'] : 
                         substr(strip_tags($product['p_description']), 0, 160),
        "image" => !empty($product['p_featured_photo']) ? 
                   $baseUrl . 'assets/products/' . $product['p_featured_photo'] : 
                   $baseUrl . 'assets/images/placeholder.png',
        "brand" => array(
            "@type" => "Brand",
            "name" => "Your Brand Name"
        ),
        "offers" => array(
            "@type" => "Offer",
            "url" => $baseUrl . 'product/' . generateUniqueSlug($product['p_name'], $product['p_id']),
            "priceCurrency" => "USD",
            "price" => $product['p_current_price'],
            "priceCurrency" => "USD",
            "availability" => $product['p_qty'] > 0 ? 
                             "https://schema.org/InStock" : 
                             "https://schema.org/OutOfStock"
        ),
        "aggregateRating" => array(
            "@type" => "AggregateRating",
            "ratingValue" => $rating,
            "reviewCount" => $reviewCount
        )
    );
    
    return '<script type="application/ld+json">' . json_encode($schema, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . '</script>' . "\n";
}

/**
 * Generate JSON-LD Schema for Organization (to add to header/footer)
 */
function generateOrganizationSchema($baseUrl = '') {
    $schema = array(
        "@context" => "https://schema.org",
        "@type" => "Organization",
        "name" => "Your Company Name",
        "url" => $baseUrl,
        "logo" => $baseUrl . "assets/images/logo.png",
        "description" => "Your company description goes here",
        "sameAs" => array(
            "https://www.facebook.com/yourpage",
            "https://www.twitter.com/yourhandle",
            "https://www.instagram.com/yourhandle",
            "https://www.linkedin.com/company/yourcompany"
        ),
        "contactPoint" => array(
            "@type" => "ContactPoint",
            "contactType" => "Customer Service",
            "telephone" => "+1-XXX-XXX-XXXX",
            "email" => "support@yourcompany.com"
        )
    );
    
    return '<script type="application/ld+json">' . json_encode($schema, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . '</script>' . "\n";
}

/**
 * Generate JSON-LD Schema for Breadcrumb Navigation
 */
function generateBreadcrumbSchema($breadcrumbs, $baseUrl = '') {
    $items = array();
    
    foreach ($breadcrumbs as $index => $crumb) {
        $items[] = array(
            "@type" => "ListItem",
            "position" => $index + 1,
            "name" => $crumb['name'],
            "item" => $baseUrl . $crumb['url']
        );
    }
    
    $schema = array(
        "@context" => "https://schema.org",
        "@type" => "BreadcrumbList",
        "itemListElement" => $items
    );
    
    return '<script type="application/ld+json">' . json_encode($schema, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . '</script>' . "\n";
}

/**
 * Generate JSON-LD Schema for LocalBusiness (if applicable)
 */
function generateLocalBusinessSchema($businessData, $baseUrl = '') {
    $schema = array(
        "@context" => "https://schema.org",
        "@type" => "LocalBusiness",
        "name" => $businessData['name'],
        "image" => $baseUrl . "assets/images/business-image.jpg",
        "description" => $businessData['description'],
        "address" => array(
            "@type" => "PostalAddress",
            "streetAddress" => $businessData['street'] ?? "123 Main St",
            "addressLocality" => $businessData['city'] ?? "City",
            "addressRegion" => $businessData['state'] ?? "State",
            "postalCode" => $businessData['zip'] ?? "12345",
            "addressCountry" => "US"
        ),
        "telephone" => $businessData['phone'] ?? "+1-XXX-XXX-XXXX",
        "url" => $baseUrl,
        "sameAs" => array(
            "https://www.facebook.com/yourpage",
            "https://www.instagram.com/yourhandle"
        )
    );
    
    return '<script type="application/ld+json">' . json_encode($schema, JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT) . '</script>' . "\n";
}

/**
 * Generate OG:URL for proper social sharing canonicalization
 */
function generateOpenGraphUrl($canonicalUrl) {
    return '<meta property="og:url" content="' . htmlspecialchars($canonicalUrl, ENT_QUOTES, 'UTF-8') . '" />' . "\n";
}

/**
 * Helper to require SEO functions from helpers file
 */
if (!function_exists('generateSlug')) {
    require_once('seo_helpers.php');
}
?>
