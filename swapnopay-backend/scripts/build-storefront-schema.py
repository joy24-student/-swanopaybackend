"""Build the isolated PHP storefront schema; never copy demo/customer/credential seeds."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
source = root / 'supabase/SHOP_POSTGRES_SCHEMA.sql'
sql = source.read_text(encoding='utf-8')
for old, new in [("DEFAULT 'Your,", "DEFAULT 'Your Store',"), ("DEFAULT 'PayPal,Bank,", "DEFAULT 'Cash on Delivery',"), ("DEFAULT '3-5,", "DEFAULT '3-5 days',"), ("DEFAULT '10-20,", "DEFAULT '10-20 days',")]:
    sql = sql.replace(old, new)
source.write_text(sql, encoding='utf-8', newline='\n')
sql = sql.replace("\\'", "''")
tables = re.findall(r'CREATE TABLE IF NOT EXISTS "(tbl_\w+)" \(\n(.*?)\n\);', sql, re.S)
assert len(tables) == 66, len(tables)
output = ['-- Version 1. Run only in a NEW merchant schema in the dedicated hosting database.\n-- No Supabase dependencies, shared public tables, demo customers or default credentials.\n']
for table, body in tables:
    lines = []
    for line in body.splitlines():
        if 'merchant_id UUID REFERENCES' in line:
            continue
        # Legacy settings/pages/products expect non-null strings/numbers. Defaults
        # make an empty store render without importing a live merchant's settings.
        if table in ('tbl_settings','tbl_page','tbl_product','tbl_payment','tbl_order','tbl_customer') and 'NOT NULL' in line and 'DEFAULT' not in line and 'PRIMARY KEY' not in line:
            default = "''" if re.search(r'\b(TEXT|VARCHAR|CHAR)\b', line) else '0'
            line = line.replace('NOT NULL', 'NOT NULL DEFAULT ' + default)
        if table == 'tbl_settings' and '"BASE_URL"' in line:
            line = '  "BASE_URL" TEXT NOT NULL DEFAULT \'\','
        if table == 'tbl_product' and re.search(r'"p_(current|old)_price"', line):
            line = re.sub(r'VARCHAR\(10\)', 'NUMERIC(12,2)', line).replace("DEFAULT ''", 'DEFAULT 0')
        if table == 'tbl_payment' and '"paid_amount"' in line:
            line = line.replace('INTEGER', 'NUMERIC(12,2)')
        if table == 'tbl_order' and '"unit_price"' in line:
            line = re.sub(r'VARCHAR\(\d+\)|INTEGER', 'NUMERIC(12,2)', line).replace("DEFAULT ''", 'DEFAULT 0')
        if table == 'tbl_order' and '"quantity"' in line:
            line = '  "quantity" INTEGER NOT NULL CHECK (quantity>0),'
        if table == 'tbl_order' and '"product_id"' in line:
            line = '  "product_id" INTEGER NOT NULL,'
        lines.append(line)
    output.append(f'CREATE TABLE IF NOT EXISTS "{table}" (\n' + '\n'.join(lines) + '\n);\n')

# Only public reference data is copied. Statements can include semicolons inside
# quoted language strings, so scan SQL strings instead of splitting on ';'.
def statement_at(start):
    quote = False
    i = start
    while i < len(sql):
        if sql[i] == "'":
            if quote and i+1 < len(sql) and sql[i+1] == "'":
                i += 2
                continue
            quote = not quote
        if sql[i] == ';' and not quote:
            return sql[start:i+1]
        i += 1
    raise ValueError('Unterminated seed')

for table, pk in [('tbl_language','lang_id'),('tbl_country','country_id'),('tbl_size','size_id'),('tbl_color','color_id')]:
    seed = statement_at(sql.index(f'INSERT INTO "{table}"'))
    output.append(seed)
    output.append(f"SELECT setval(pg_get_serial_sequence('{table}','{pk}'),(SELECT max({pk}) FROM {table}));")
output.append('''
CREATE OR REPLACE FUNCTION rand() RETURNS double precision LANGUAGE sql AS 'SELECT random()';
ALTER TABLE tbl_product ADD COLUMN IF NOT EXISTS source_id text UNIQUE;
ALTER TABLE tbl_product ALTER COLUMN p_featured_photo SET DEFAULT 'placeholder.svg';
ALTER TABLE tbl_product ADD CONSTRAINT product_stock_nonnegative CHECK (p_qty >= 0);
ALTER TABLE tbl_product ADD CONSTRAINT product_price_nonnegative CHECK (p_current_price >= 0);
ALTER TABLE tbl_settings ADD COLUMN IF NOT EXISTS theme_color varchar(7) NOT NULL DEFAULT '#4F46E5';
ALTER TABLE tbl_settings ADD COLUMN IF NOT EXISTS currency_code varchar(3) NOT NULL DEFAULT 'BDT';
ALTER TABLE tbl_settings ADD COLUMN popup_on_off smallint NOT NULL DEFAULT 0;
ALTER TABLE tbl_settings ADD COLUMN popup_text text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN popup_link text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN popup_photo text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN sms_api_key text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN sms_sender_id text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN sms_feature_on_off smallint NOT NULL DEFAULT 0;
ALTER TABLE tbl_settings ADD COLUMN facebook_url text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN instagram_url text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN linkedin_url text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN twitter_url text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN youtube_url text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN copyright_text text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN footer_about_us text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN payment_verified_image text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_about text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_contact text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_customer_panel text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_faq text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_payment text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_photo_gallery text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_privacy text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_return_policy text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_shipping text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_team text NOT NULL DEFAULT '';
ALTER TABLE tbl_settings ADD COLUMN banner_terms text NOT NULL DEFAULT '';
ALTER TABLE tbl_product ADD COLUMN slug text UNIQUE;
ALTER TABLE tbl_top_category ADD COLUMN slug text UNIQUE;
ALTER TABLE tbl_mid_category ADD COLUMN slug text UNIQUE;
ALTER TABLE tbl_end_category ADD COLUMN slug text UNIQUE;
ALTER TABLE tbl_product_size ADD CONSTRAINT product_size_unique UNIQUE(p_id,size_id);
ALTER TABLE tbl_product_color ADD CONSTRAINT product_color_unique UNIQUE(p_id,color_id);
ALTER TABLE tbl_product_size ADD CONSTRAINT product_size_product_fk FOREIGN KEY(p_id) REFERENCES tbl_product(p_id) ON DELETE CASCADE;
ALTER TABLE tbl_product_size ADD CONSTRAINT product_size_option_fk FOREIGN KEY(size_id) REFERENCES tbl_size(size_id) ON DELETE RESTRICT;
ALTER TABLE tbl_product_color ADD CONSTRAINT product_color_product_fk FOREIGN KEY(p_id) REFERENCES tbl_product(p_id) ON DELETE CASCADE;
ALTER TABLE tbl_product_color ADD CONSTRAINT product_color_option_fk FOREIGN KEY(color_id) REFERENCES tbl_color(color_id) ON DELETE RESTRICT;
ALTER TABLE tbl_product_photo ADD CONSTRAINT product_photo_product_fk FOREIGN KEY(p_id) REFERENCES tbl_product(p_id) ON DELETE CASCADE;
ALTER TABLE tbl_mid_category ADD CONSTRAINT mid_category_parent_fk FOREIGN KEY(tcat_id) REFERENCES tbl_top_category(tcat_id) ON DELETE RESTRICT;
ALTER TABLE tbl_end_category ADD CONSTRAINT end_category_parent_fk FOREIGN KEY(mcat_id) REFERENCES tbl_mid_category(mcat_id) ON DELETE RESTRICT;
ALTER TABLE tbl_product ADD CONSTRAINT product_category_fk FOREIGN KEY(ecat_id) REFERENCES tbl_end_category(ecat_id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX storefront_payment_reference ON tbl_payment(payment_id);
CREATE INDEX storefront_order_payment ON tbl_order(payment_id);
CREATE INDEX storefront_active_products ON tbl_product(p_is_active,p_id);
CREATE UNIQUE INDEX storefront_admin_email ON tbl_user(lower(email));
CREATE UNIQUE INDEX storefront_customer_email ON tbl_customer(lower(cust_email));
CREATE UNIQUE INDEX storefront_customer_review ON tbl_review(cust_id,product_id);
ALTER TABLE tbl_review ADD CONSTRAINT storefront_review_rating CHECK (rating BETWEEN 1 AND 5);
CREATE TABLE shop_login_attempts (attempt_key text PRIMARY KEY, failures integer NOT NULL DEFAULT 0, last_attempt timestamptz NOT NULL DEFAULT now());
INSERT INTO tbl_settings(id,logo,favicon,meta_title_home,footer_copyright,featured_product_title,latest_product_title,popular_product_title,
 home_featured_product_on_off,home_latest_product_on_off,total_featured_product_home,total_latest_product_home,total_popular_product_home,
 home_map_on_off,home_newsletter_on_off,home_brand_on_off,home_slider_on_off,home_features_on_off,home_category_on_off,
 multi_vendor_active,coin_system_active,chat_system_on_off,cod_enabled,payment_methods,product_voucher_code,product_voucher_discount,
 slider_side_banner_img,extra_footer_section_enable,hide_banner_desktop,hide_banner_mobile,hide_free_delivery_desktop,hide_free_delivery_mobile)
 VALUES(1,'logo.svg','logo.svg','Your Store','All rights reserved.','Featured products','New arrivals','Popular products',
 1,1,8,12,8,0,0,0,0,0,1,0,0,0,1,'Cash on Delivery','',0,'',0,1,1,1,1);
INSERT INTO tbl_page(id,about_title,about_content,faq_title,contact_title) VALUES(1,'About our store','Welcome to our online store.','Frequently asked questions','Contact us');
INSERT INTO tbl_top_category(tcat_id,tcat_name,show_on_menu,tcat_order,photo) VALUES(1,'Shop',1,1,'placeholder.svg');
INSERT INTO tbl_mid_category(mcat_id,mcat_name,tcat_id) VALUES(1,'All products',1);
INSERT INTO tbl_end_category(ecat_id,ecat_name,mcat_id) VALUES(1,'General',1);
INSERT INTO tbl_shipping_cost_all(sca_id,amount) VALUES(1,0);
SELECT setval(pg_get_serial_sequence('tbl_user','id'),1);
SELECT setval(pg_get_serial_sequence('tbl_settings','id'),1);
SELECT setval(pg_get_serial_sequence('tbl_page','id'),1);
SELECT setval(pg_get_serial_sequence('tbl_top_category','tcat_id'),1);
SELECT setval(pg_get_serial_sequence('tbl_mid_category','mcat_id'),1);
SELECT setval(pg_get_serial_sequence('tbl_end_category','ecat_id'),1);
SELECT setval(pg_get_serial_sequence('tbl_shipping_cost_all','sca_id'),1);
''')
destination = root / 'swapnopay-backend/sql/storefront.sql'
social_names = ['Facebook','Twitter','LinkedIn','Google Plus','Pinterest','YouTube','Instagram','Tumblr','Flickr','Reddit','Snapchat','WhatsApp','Quora','StumbleUpon','Delicious','Digg']
for name in social_names:
    output.append(f"INSERT INTO tbl_social(social_name,social_url) VALUES('{name}','');")
destination.parent.mkdir(exist_ok=True)
destination.write_text('\n'.join(output), encoding='utf-8', newline='\n')
print(f'Generated {len(tables)} tables with clean reference seeds: {destination}')
