/**
 * Lazy Load Implementation
 * Handles lazy loading of images and deferred content loading
 * Uses Intersection Observer API for optimal performance
 */

class LazyLoader {
    constructor(options = {}) {
        this.options = {
            threshold: options.threshold || 0.1,
            rootMargin: options.rootMargin || '50px',
            loadingClass: options.loadingClass || 'loading',
            loadedClass: options.loadedClass || 'loaded',
            errorClass: options.errorClass || 'error',
            ...options
        };

        this.imageObserver = null;
        this.contentObserver = null;
        this.initialize();
    }

    /**
     * Initialize Intersection Observers
     */
    initialize() {
        // Observer for images
        this.imageObserver = new IntersectionObserver(
            (entries) => this.handleImageIntersection(entries),
            {
                threshold: this.options.threshold,
                rootMargin: this.options.rootMargin
            }
        );

        // Observer for content
        this.contentObserver = new IntersectionObserver(
            (entries) => this.handleContentIntersection(entries),
            {
                threshold: this.options.threshold,
                rootMargin: this.options.rootMargin
            }
        );

        // Observe all lazy-loadable images
        this.observeImages();
        
        // Observe all lazy-loadable content
        this.observeContent();
    }

    /**
     * Observe all images with data-src attribute
     */
    observeImages() {
        const images = document.querySelectorAll('img[data-src]');
        images.forEach(img => {
            this.imageObserver.observe(img);
        });
    }

    /**
     * Observe all content with data-src or data-content attribute
     */
    observeContent() {
        const elements = document.querySelectorAll('[data-content][data-lazy="true"]');
        elements.forEach(el => {
            this.contentObserver.observe(el);
        });
    }

    /**
     * Handle image intersection
     */
    handleImageIntersection(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                this.loadImage(entry.target);
                this.imageObserver.unobserve(entry.target);
            }
        });
    }

    /**
     * Handle content intersection
     */
    handleContentIntersection(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                this.loadContent(entry.target);
                this.contentObserver.unobserve(entry.target);
            }
        });
    }

    /**
     * Load image
     */
    loadImage(img) {
        const src = img.dataset.src;
        const srcset = img.dataset.srcset;

        if (!src) return;

        // Add loading class
        img.classList.add(this.options.loadingClass);

        // Create image to preload
        const tempImg = new Image();

        // Handle srcset
        if (srcset) {
            tempImg.srcset = srcset;
            img.srcset = srcset;
        }

        // Load image
        tempImg.onload = () => {
            img.src = src;
            img.classList.remove(this.options.loadingClass);
            img.classList.add(this.options.loadedClass);
            
            // Remove data attributes
            delete img.dataset.src;
            delete img.dataset.srcset;

            // Trigger callback
            if (this.options.onImageLoaded) {
                this.options.onImageLoaded(img);
            }
        };

        tempImg.onerror = () => {
            img.classList.remove(this.options.loadingClass);
            img.classList.add(this.options.errorClass);

            // Use fallback if available
            if (img.dataset.fallback) {
                img.src = img.dataset.fallback;
            }

            if (this.options.onImageError) {
                this.options.onImageError(img);
            }
        };

        // Start loading
        if (srcset) {
            tempImg.sizes = img.sizes || 'auto';
        }
        tempImg.src = src;
    }

    /**
     * Load content (AJAX or other)
     */
    async loadContent(element) {
        const url = element.dataset.content;
        
        if (!url) return;

        element.classList.add(this.options.loadingClass);

        try {
            const response = await fetch(url);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const html = await response.text();
            element.innerHTML = html;
            element.classList.remove(this.options.loadingClass);
            element.classList.add(this.options.loadedClass);

            if (this.options.onContentLoaded) {
                this.options.onContentLoaded(element);
            }

        } catch (error) {
            console.error('Error loading content:', error);
            element.classList.remove(this.options.loadingClass);
            element.classList.add(this.options.errorClass);

            if (this.options.onContentError) {
                this.options.onContentError(element, error);
            }
        }
    }

    /**
     * Add new images to lazy loading (for dynamically added content)
     */
    addImages(container = document) {
        const images = container.querySelectorAll('img[data-src]');
        images.forEach(img => {
            if (!img.classList.contains(this.options.loadedClass)) {
                this.imageObserver.observe(img);
            }
        });
    }

    /**
     * Add new content to lazy loading (for dynamically added content)
     */
    addContent(container = document) {
        const elements = container.querySelectorAll('[data-content][data-lazy="true"]');
        elements.forEach(el => {
            if (!el.classList.contains(this.options.loadedClass)) {
                this.contentObserver.observe(el);
            }
        });
    }

    /**
     * Manually load image immediately
     */
    forceLoadImage(img) {
        if (this.imageObserver) {
            this.imageObserver.unobserve(img);
        }
        this.loadImage(img);
    }

    /**
     * Manually load content immediately
     */
    forceLoadContent(element) {
        if (this.contentObserver) {
            this.contentObserver.unobserve(element);
        }
        this.loadContent(element);
    }

    /**
     * Destroy lazy loader
     */
    destroy() {
        if (this.imageObserver) {
            this.imageObserver.disconnect();
        }
        if (this.contentObserver) {
            this.contentObserver.disconnect();
        }
    }
}

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', function() {
    // Create global instance
    window.lazyLoader = new LazyLoader({
        threshold: 0.1,
        rootMargin: '50px',
        onImageLoaded: function(img) {
            // Custom callback for image loaded
            console.log('Image loaded:', img.src);
        },
        onContentLoaded: function(element) {
            // Custom callback for content loaded
            console.log('Content loaded:', element.id);
        }
    });
});

// Export for use as module
if (typeof module !== 'undefined' && module.exports) {
    module.exports = LazyLoader;
}
