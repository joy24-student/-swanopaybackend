document.addEventListener('DOMContentLoaded', function() {
    // --- Global Utility Functions ---
    // Function to generate star rating HTML
    function generateStarRating(avg_rating) {
        let stars = '';
        if (avg_rating === 0) {
            stars = '<span class="no-rating">No reviews yet</span>';
        } else {
            for (let i = 1; i <= 5; i++) {
                stars += (i <= avg_rating) ? '<i class="fa fa-star"></i>' : '<i class="fa fa-star-o"></i>';
            }
        }
        return stars;
    }

    // Function to create a product card HTML string
    function createProductCardHtml(product) {
        const baseUrl = '<?php echo BASE_URL; ?>'; // Use PHP to get BASE_URL
        const langValue1 = '<?php echo LANG_VALUE_1; ?>'; // Currency symbol

        return `
            <div class="product-card-wrapper col-xs-6 col-sm-4 col-md-3">
                <a href="${baseUrl}product.php?id=${product.p_id}" class="product-card">
                    <div class="product-image-container">
                        <img src="${baseUrl}assets/uploads/${product.p_featured_photo}" alt="${product.p_name}" loading="lazy">
                        ${product.p_old_price > 0 && product.p_old_price > product.p_current_price ? `
                            <div class="discount-badge">
                                ${Math.round(((product.p_old_price - product.p_current_price) / product.p_old_price) * 100)}% OFF
                            </div>
                        ` : ''}
                    </div>
                    <div class="product-info">
                        <h4 class="product-name">${product.p_name}</h4>
                        <div class="product-price">
                            <span class="current-price">${langValue1}${product.p_current_price.toFixed(2)}</span>
                            ${product.p_old_price > 0 ? `<span class="old-price">${langValue1}${product.p_old_price.toFixed(2)}</span>` : ''}
                        </div>
                        <div class="product-rating">
                            ${generateStarRating(product.avg_rating)}
                            ${product.total_reviews_count > 0 ? `<span class="review-count">(${product.total_reviews_count})</span>` : ''}
                        </div>
                        ${product.p_qty == 0 ? `<div class="out-of-stock-label">Out Of Stock</div>` : ''}
                    </div>
                </a>
            </div>
        `;
    }

    // --- Chakelton (Skeleton) Loading Logic ---
    // Note: This skeleton logic is primarily for index.php's dynamic sections.
    // For product.php, the skeleton is a static HTML/CSS placeholder shown then hidden.
    const skeletonLoaders = document.querySelectorAll('.skeleton-grid');

    function showSkeletons(container) {
        container.style.display = 'flex';
    }

    function hideSkeletons(container) {
        container.style.display = 'none';
    }

    // --- Infinite Scroll for Popular Products (if applicable to index.php) ---
    // (Ensure these variables are correctly passed from PHP in index.php if used)
    let infiniteScrollOffset = 0; // Placeholder, should be from PHP
    let infiniteScrollLimit = 12; // Placeholder
    let isInfiniteLoading = false;
    let hasMoreProducts = true;

    const infiniteScrollContainer = document.getElementById('infinite-scroll-container');
    const infiniteScrollSpinner = document.getElementById('infinite-scroll-spinner');

    if (infiniteScrollContainer) {
        // Initialize these values from PHP in index.php
        // infiniteScrollOffset = <?php echo $total_popular_product_home; ?>;
        // infiniteScrollLimit = 12; // Or whatever you set

        window.addEventListener('scroll', () => {
            if ((window.innerHeight + window.scrollY) >= (document.body.offsetHeight - 500) && !isInfiniteLoading && hasMoreProducts) {
                // loadMoreInfiniteProducts(); // Call this function if infinite scroll is active
            }
        });
    }


    // --- Recommendations (Search History) Logic ---
    const recommendedSection = document.getElementById('recommendation-section');
    const recommendedContainer = document.getElementById('recommended-products-container');
    const recommendedSkeletonLoader = document.getElementById('recommended-skeleton-loader');

    if (recommendedSection && recommendedContainer) {
        showSkeletons(recommendedSkeletonLoader); // Show skeleton initially

        const searchHistory = JSON.parse(localStorage.getItem('searchHistory')) || [];
        if (searchHistory.length > 0) {
            const searchTerms = searchHistory.join(',');
            // fetch(`<?php echo BASE_URL; ?>fetch_recommendations.php?search_terms=${encodeURIComponent(searchTerms)}`)
            //     .then(response => response.json())
            //     .then(data => {
            //         hideSkeletons(recommendedSkeletonLoader); // Hide skeleton on load
            //         if (data.success && data.products.length > 0) {
            //             recommendedSection.style.display = 'block';
            //             data.products.forEach(product => {
            //                 recommendedContainer.insertAdjacentHTML('beforeend', createProductCardHtml(product));
            //             });
            //         } else {
            //             recommendedSection.style.display = 'none'; // Hide section if no recommendations
            //         }
            //     })
            //     .catch(error => {
            //         console.error('Error fetching recommendations:', error);
            //         hideSkeletons(recommendedSkeletonLoader);
            //         recommendedSection.style.display = 'none';
            //     });
        } else {
            hideSkeletons(recommendedSkeletonLoader);
            recommendedSection.style.display = 'none'; // Hide section if no search history
        }
    }

    // --- Recently Viewed Items Logic ---
    const recentlyViewedSection = document.getElementById('recently-viewed-section');
    const recentlyViewedContainer = document.getElementById('recently-viewed-products-container');
    const recentlyViewedSkeletonLoader = document.getElementById('recently-viewed-skeleton-loader');

    if (recentlyViewedSection && recentlyViewedContainer) {
        showSkeletons(recentlyViewedSkeletonLoader); // Show skeleton initially

        const recentlyViewedItems = JSON.parse(localStorage.getItem('recentlyViewed')) || [];
        if (recentlyViewedItems.length > 0) {
            const productIds = recentlyViewedItems.map(item => item.id).join(',');
            // fetch(`<?php echo BASE_URL; ?>fetch_products_by_ids.php?ids=${encodeURIComponent(productIds)}`)
            //     .then(response => response.json())
            //     .then(data => {
            //         hideSkeletons(recentlyViewedSkeletonLoader); // Hide skeleton on load
            //         if (data.success && data.products.length > 0) {
            //             recentlyViewedSection.style.display = 'block';
            //             data.products.forEach(product => {
            //                 recentlyViewedContainer.insertAdjacentHTML('beforeend', createProductCardHtml(product));
            //             });
            //         } else {
            //             recentlyViewedSection.style.display = 'none'; // Hide section if no recently viewed
            //         }
            //     })
            //     .catch(error => {
            //         console.error('Error fetching recently viewed products:', error);
            //         hideSkeletons(recentlyViewedSkeletonLoader);
            //         recentlyViewedSection.style.display = 'none';
            //     });
        } else {
            hideSkeletons(recentlyViewedSkeletonLoader);
            recentlyViewedSection.style.display = 'none'; // Hide section if no recently viewed
        }
    }

    // --- Desktop Sidebar Toggle ---
    const sidebarToggleBtn = document.getElementById('sidebar-toggle-btn');
    const desktopSidebar = document.querySelector('.desktop-sidebar');
    const mainContentArea = document.querySelector('.main-content-area');
    const topBarDesktop = document.querySelector('.top-bar-desktop');

    if (sidebarToggleBtn && desktopSidebar && mainContentArea && topBarDesktop) {
        sidebarToggleBtn.addEventListener('click', function() {
            desktopSidebar.classList.toggle('collapsed');
            mainContentArea.classList.toggle('sidebar-collapsed');
            topBarDesktop.classList.toggle('sidebar-collapsed');
        });
    }

    // --- Sidebar Submenu Toggle ---
    document.querySelectorAll('.sidebar-menu .has-submenu').forEach(item => {
        item.addEventListener('click', function(e) {
            e.preventDefault(); // Prevent default link behavior
            this.classList.toggle('open');
            const submenu = this.nextElementSibling;
            if (submenu) {
                submenu.style.display = submenu.style.display === 'block' ? 'none' : 'block';
            }
        });
    });

    // --- Mobile Menu Toggle (if you decide to use a mobile sidebar) ---
    const mobileMenuToggle = document.getElementById('mobile-menu-toggle');
    // You would typically have a mobile sidebar element to toggle
    // const mobileSidebar = document.querySelector('.mobile-sidebar');
    // if (mobileMenuToggle && mobileSidebar) {
    //     mobileMenuToggle.addEventListener('click', function() {
    //         mobileSidebar.classList.toggle('open');
    //     });
    // }

    // --- Desktop Search Functionality ---
    const desktopSearchInput = document.getElementById('desktop-search-input');
    const desktopSearchBtn = document.getElementById('desktop-search-btn');

    if (desktopSearchBtn && desktopSearchInput) {
        desktopSearchBtn.addEventListener('click', function() {
            const searchTerm = desktopSearchInput.value.trim();
            if (searchTerm) {
                window.location.href = `<?php echo BASE_URL; ?>search-result.php?search_text=${encodeURIComponent(searchTerm)}`;
            }
        });
        desktopSearchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                desktopSearchBtn.click();
            }
        });
    }

    // --- Owl Carousel Initialization ---
    // Make sure you have Owl Carousel JS and CSS included
    if (typeof jQuery !== 'undefined' && jQuery.fn.owlCarousel) {
        jQuery('.main-slider-carousel').owlCarousel({
            loop: true,
            margin: 0,
            nav: true,
            dots: true,
            autoplay: true,
            autoplayTimeout: 5000,
            autoplayHoverPause: true,
            responsive: {
                0: { items: 1 },
                600: { items: 1 },
                1000: { items: 1 }
            }
        });

        jQuery('.flash-sale-carousel').owlCarousel({
            loop: false, // Flash sales might not loop
            margin: 15,
            nav: true,
            dots: false,
            responsive: {
                0: { items: 2 },
                600: { items: 3 },
                1000: { items: 4 }
            }
        });
    }

    // --- Flash Sale Countdown Timer ---
    const flashSaleCountdown = document.getElementById('flash-sale-countdown');
    if (flashSaleCountdown) {
        // You would fetch a real end time from your database for a real flash sale
        const endTime = new Date();
        endTime.setHours(endTime.getHours() + 2); // Example: 2 hours from now for demonstration

        const updateCountdown = setInterval(() => {
            const now = new Date().getTime();
            const distance = endTime - now;

            const hours = Math.floor((distance % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const minutes = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60));
            const seconds = Math.floor((distance % (1000 * 60)) / 1000);

            if (distance < 0) {
                clearInterval(updateCountdown);
                flashSaleCountdown.innerHTML = "EXPIRED";
            } else {
                flashSaleCountdown.innerHTML = `${hours.toString().padStart(2, '0')}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s`;
            }
        }, 1000);
    }

    // --- Skeleton Loading for Product Page (Specific) ---
    const productPageSkeletonLoading = document.querySelector('.product-detail-wrapper .skeleton-loading');
    const productContentActual = document.querySelector('.product-detail-wrapper .product-content-actual');

    if (productPageSkeletonLoading && productContentActual) {
        setTimeout(() => {
            productPageSkeletonLoading.style.display = 'none';
            productContentActual.style.display = 'block';
        }, 1000); // Adjust this delay as needed
    }

    // --- Image Gallery Preview (Product Page Specific) ---
    const mainProductImage = document.getElementById('mainProductImage');
    const thumbnails = document.querySelectorAll('.product-thumbnails .thumbnail');

    if (mainProductImage && thumbnails.length > 0) {
        thumbnails.forEach(thumbnail => {
            thumbnail.addEventListener('click', function() {
                thumbnails.forEach(t => t.classList.remove('active'));
                this.classList.add('active');
                mainProductImage.style.opacity = 0;
                setTimeout(() => {
                    mainProductImage.src = this.dataset.fullImage;
                    mainProductImage.style.opacity = 1;
                }, 200);
            });
        });
    }

    // --- Quantity Selector (Product Page Specific) ---
    const qtyInput = document.querySelector('.qty-input');
    if (qtyInput) {
        const maxQty = parseInt(qtyInput.getAttribute('max'));
        document.querySelectorAll('.qty-btn').forEach(button => {
            button.addEventListener('click', function() {
                let value = parseInt(qtyInput.value);
                if (this.classList.contains('minus')) {
                    if (value > 1) {
                        qtyInput.value = value - 1;
                    }
                } else {
                    if (value < maxQty) {
                        qtyInput.value = value + 1;
                    }
                }
            });
        });
    }

    // --- Product Option (Size/Color) Name Update (Product Page Specific) ---
    const sizeSelect = document.getElementById('size_id');
    const sizeNameHidden = document.getElementById('size_name_hidden');
    if (sizeSelect && sizeNameHidden) {
        sizeSelect.addEventListener('change', function() {
            sizeNameHidden.value = this.options[this.selectedIndex].dataset.sizeName;
        });
        sizeNameHidden.value = sizeSelect.options[sizeSelect.selectedIndex].dataset.sizeName;
    }

    const colorSelect = document.getElementById('color_id');
    const colorNameHidden = document.getElementById('color_name_hidden');
    if (colorSelect && colorNameHidden) {
        colorSelect.addEventListener('change', function() {
            colorNameHidden.value = this.options[this.selectedIndex].dataset.colorName;
        });
        colorNameHidden.value = colorSelect.options[colorSelect.selectedIndex].dataset.colorName;
    }

    // --- Wishlist Toggle Functionality (Product Page Specific) ---
    const wishlistBtn = document.querySelector('.wishlist-btn');
    if (wishlistBtn) {
        wishlistBtn.addEventListener('click', function() {
            const productId = this.dataset.productId;
            let action = 'add';
            if (this.classList.contains('active')) {
                action = 'remove';
            }

            fetch('<?php echo BASE_URL; ?>wishlist_action.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `product_id=${encodeURIComponent(productId)}&action=${encodeURIComponent(action)}&csrf_token=<?php echo $csrf->getTokenValue() ?? ''; ?>` // Added ?? '' for safety
            })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    const wishlistText = wishlistBtn.querySelector('.wishlist-text');
                    if (action === 'add') {
                        wishlistBtn.classList.add('active');
                        wishlistText.textContent = 'Remove from Wishlist';
                    } else {
                        wishlistBtn.classList.remove('active');
                        wishlistText.textContent = 'Add to Wishlist';
                    }
                } else {
                    alert('Error: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error toggling wishlist:', error);
                alert('An error occurred while updating wishlist.');
            });
        });
    }

    // --- AI Chat Modal Logic (Product Page Specific) ---
    const aiChatModal = document.getElementById('aiChatModal');
    const askAiBtn = document.querySelector('.ask-ai-btn');
    const closeButtonAiChat = document.querySelector('.close-button-ai-chat');
    const aiProductNameSpan = document.getElementById('aiProductName');
    const chatMessages = document.getElementById('chatMessages');
    const chatInput = document.getElementById('chatInput');
    const sendChatBtn = document.getElementById('sendChatBtn');
    const directChatOption = document.querySelector('.direct-chat-options'); // Corrected selector

    if (askAiBtn) {
        askAiBtn.addEventListener('click', function() {
            const productName = this.dataset.productName;
            aiProductNameSpan.textContent = productName;
            aiChatModal.style.display = 'block';
            chatMessages.innerHTML = '<div class="chat-message ai">Hello! I\'m an AI assistant. How can I help you with ' + productName + '?</div>';
            if (directChatOption) directChatOption.style.display = 'none'; // Hide direct chat options initially
        });
    }

    if (closeButtonAiChat) {
        closeButtonAiChat.addEventListener('click', function() {
            aiChatModal.style.display = 'none';
            chatMessages.innerHTML = ''; // Clear chat history
        });
    }

    if (aiChatModal) { // Only add if modal exists
        window.addEventListener('click', function(event) {
            if (event.target == aiChatModal) {
                aiChatModal.style.display = 'none';
                chatMessages.innerHTML = ''; // Clear chat history
            }
        });
    }

    if (sendChatBtn) {
        sendChatBtn.addEventListener('click', sendMessage);
        chatInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
    }

    function sendMessage() {
        const userMessage = chatInput.value.trim();
        if (userMessage === '') return;

        appendMessage(userMessage, 'user');
        chatInput.value = '';
        chatMessages.scrollTop = chatMessages.scrollHeight; // Scroll to bottom

        const loadingMessage = appendMessage('AI is typing...', 'ai loading');
        chatMessages.scrollTop = chatMessages.scrollHeight;

        fetch('<?php echo BASE_URL; ?>gemini_chat_proxy.php', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `prompt=${encodeURIComponent(userMessage)}&product_name=${encodeURIComponent(aiProductNameSpan.textContent)}&csrf_token=<?php echo $csrf->getTokenValue() ?? ''; ?>` // Added ?? '' for safety
        })
        .then(response => response.json())
        .then(data => {
            loadingMessage.remove();
            if (data.status === 'success') {
                appendMessage(data.response, 'ai');
                chatMessages.scrollTop = chatMessages.scrollHeight;
                if (directChatOption) { // Check if element exists before accessing style
                    if (data.response.toLowerCase().includes('seller') || data.response.toLowerCase().includes('directly')) {
                        directChatOption.style.display = 'block';
                    } else {
                        directChatOption.style.display = 'none';
                    }
                }
            } else {
                appendMessage('Error: ' + (data.message || 'Could not get response from AI.'), 'ai');
                chatMessages.scrollTop = chatMessages.scrollHeight;
            }
        })
        .catch(error => {
            loadingMessage.remove();
            console.error('Error calling Gemini API:', error);
            appendMessage('An error occurred while connecting to AI.', 'ai');
            chatMessages.scrollTop = chatMessages.scrollHeight;
        });
    }

    function appendMessage(text, sender) {
        const messageDiv = document.createElement('div');
        messageDiv.classList.add('chat-message', sender);
        messageDiv.textContent = text;
        chatMessages.appendChild(messageDiv);
        return messageDiv;
    }

    // --- Sticky Section Navigation and Active State (Product Page Specific) ---
    const productSectionsNav = document.getElementById('productSectionsNav');
    const sections = document.querySelectorAll('.product-section');
    const navLinks = document.querySelectorAll('#productSectionsNav .nav-link');

    if (productSectionsNav && sections.length > 0 && navLinks.length > 0) {
        function updateStickyNav() {
            const navOffsetTop = productSectionsNav.offsetTop;
            if (window.pageYOffset > navOffsetTop) {
                productSectionsNav.classList.add('sticky');
            } else {
                productSectionsNav.classList.remove('sticky');
            }
        }

        function updateActiveNavLink() {
            let currentActiveSection = '';
            sections.forEach(section => {
                const sectionTop = section.offsetTop - productSectionsNav.offsetHeight - 20;
                const sectionBottom = sectionTop + section.offsetHeight;
                if (window.pageYOffset >= sectionTop && window.pageYOffset < sectionBottom) {
                    currentActiveSection = section.id;
                }
            });

            navLinks.forEach(link => {
                link.classList.remove('active');
                if (link.dataset.target === currentActiveSection) {
                    link.classList.add('active');
                }
            });
        }

        updateStickyNav();
        updateActiveNavLink();
        window.addEventListener('scroll', () => {
            updateStickyNav();
            updateActiveNavLink();
        });

        navLinks.forEach(link => {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                const targetId = this.dataset.target;
                const targetSection = document.getElementById(targetId);
                if (targetSection) {
                    const offset = productSectionsNav.offsetHeight;
                    window.scrollTo({
                        top: targetSection.offsetTop - offset,
                        behavior: 'smooth'
                    });
                }
            });
        });
    }


    // --- Product Review Form Submission (AJAX) (Product Page Specific) ---
    const productReviewForm = document.getElementById('productReviewForm');
    const submitProductReviewBtn = document.getElementById('submitProductReviewBtn');
    const productReviewFormMessage = document.getElementById('productReviewFormMessage');

    if (productReviewForm) {
        productReviewForm.addEventListener('submit', function(e) {
            e.preventDefault();

            const productId = this.querySelector('input[name="product_id"]').value;
            const ratingInput = this.querySelector('input[name="rating"]:checked');
            const reviewTitle = this.querySelector('input[name="review_title"]').value.trim();
            const comment = this.querySelector('textarea[name="comment"]').value.trim();
            const csrfToken = this.querySelector('input[name="csrf_token"]').value;

            if (!ratingInput) {
                displayProductReviewMessage('Please select a rating.', 'alert-danger');
                return;
            }
            if (!reviewTitle) {
                displayProductReviewMessage('Review title is required.', 'alert-danger');
                return;
            }
            if (!comment) {
                displayProductReviewMessage('Comment is required.', 'alert-danger');
                return;
            }

            submitProductReviewBtn.disabled = true;
            submitProductReviewBtn.textContent = 'Submitting...';

            fetch('<?php echo BASE_URL; ?>submit_review.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `product_id=${encodeURIComponent(productId)}&rating=${encodeURIComponent(ratingInput.value)}&review_title=${encodeURIComponent(reviewTitle)}&comment=${encodeURIComponent(comment)}&csrf_token=${encodeURIComponent(csrfToken)}`
            })
            .then(response => response.json())
            .then(data => {
                submitProductReviewBtn.disabled = false;
                submitProductReviewBtn.textContent = 'Submit Review';
                if (data.status === 'success') {
                    displayProductReviewMessage(data.message, 'alert-success');
                    setTimeout(() => {
                        window.location.reload();
                    }, 1500);
                } else {
                    displayProductReviewMessage('Error: ' + data.message, 'alert-danger');
                }
            })
            .catch(error => {
                console.error('Error submitting product review:', error);
                submitProductReviewBtn.disabled = false;
                submitProductReviewBtn.textContent = 'Submit Review';
                displayProductReviewMessage('An error occurred while submitting your review.', 'alert-danger');
            });
        });
    }

    function displayProductReviewMessage(message, typeClass) {
        productReviewFormMessage.textContent = message;
        productReviewFormMessage.className = `mt-3 alert ${typeClass}`;
        productReviewFormMessage.style.display = 'block';
    }
});