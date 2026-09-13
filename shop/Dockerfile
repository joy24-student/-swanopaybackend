FROM php:8.2-fpm-alpine

# Install system dependencies and build libraries
COPY --from=composer:2 /usr/bin/composer /usr/local/bin/composer
RUN apk add --no-cache \
    postgresql-dev \
    libpng-dev \
    libjpeg-turbo-dev \
    freetype-dev \
    libzip-dev \
    icu-dev \
    oniguruma-dev \
    curl \
    unzip

# Configure and install PHP extensions
RUN docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install \
        pdo \
        pdo_pgsql \
        pdo_mysql \
        gd \
        zip \
        intl \
        mbstring \
        bcmath \
        opcache

# Production OPcache & PHP settings
RUN echo "opcache.enable=1" >> /usr/local/etc/php/conf.d/docker-php-ext-opcache.ini \
    && echo "opcache.memory_consumption=128" >> /usr/local/etc/php/conf.d/docker-php-ext-opcache.ini \
    && echo "opcache.interned_strings_buffer=8" >> /usr/local/etc/php/conf.d/docker-php-ext-opcache.ini \
    && echo "opcache.max_accelerated_files=4000" >> /usr/local/etc/php/conf.d/docker-php-ext-opcache.ini \
    && echo "upload_max_filesize=32M" >> /usr/local/etc/php/conf.d/uploads.ini \
    && echo "post_max_size=32M" >> /usr/local/etc/php/conf.d/uploads.ini \
    && echo "memory_limit=256M" >> /usr/local/etc/php/conf.d/uploads.ini

WORKDIR /var/www/html

RUN addgroup -g 10001 shop-runtime && addgroup www-data shop-runtime
COPY runtime.ini /usr/local/etc/php/conf.d/store-runtime.ini

COPY . /var/www/html
RUN composer install --no-dev --prefer-dist --no-interaction --optimize-autoloader

RUN chown -R www-data:www-data /var/www/html \
    && chmod -R 755 /var/www/html

EXPOSE 9000

CMD ["php-fpm"]
