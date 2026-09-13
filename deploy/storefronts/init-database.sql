-- Dedicated hosting database only. Tenant roles get access solely to their schema.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE storefronts FROM PUBLIC;
GRANT CONNECT ON DATABASE storefronts TO PUBLIC;
