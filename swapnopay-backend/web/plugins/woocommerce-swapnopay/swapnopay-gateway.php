<?php
/**
 * Plugin Name: SwapnoPay Payment Gateway for WooCommerce
 * Plugin URI: https://swapnopay.com
 * Description: Self-hosted automated payment verification gateway for bKash, Nagad, Rocket, and Upay.
 * Version: 1.0.0
 * Author: SwapnoPay
 * Author URI: https://swapnopay.com
 * License: GPL-2.0+
 */

if ( ! defined( 'ABSPATH' ) ) {
    exit; // Exit if accessed directly
}

add_action( 'plugins_loaded', 'init_swapnopay_gateway_class' );

function init_swapnopay_gateway_class() {
    if ( ! class_exists( 'WC_Payment_Gateway' ) ) return;

    class WC_Gateway_SwapnoPay extends WC_Payment_Gateway {

        public function __construct() {
            $this->id                 = 'swapnopay';
            $this->icon               = apply_filters( 'woocommerce_swapnopay_icon', '' );
            $this->has_fields         = false;
            $this->method_title       = __( 'SwapnoPay Gateway', 'woocommerce-swapnopay' );
            $this->method_description = __( 'Accept automated MFS payments (bKash, Nagad, Rocket, Upay) with instant SMS verification.', 'woocommerce-swapnopay' );

            // Load the settings
            $this->init_form_fields();
            $this->init_settings();

            // Define variables
            $this->title          = $this->get_option( 'title' );
            $this->description    = $this->get_option( 'description' );
            $this->merchant_id   = $this->get_option( 'merchant_id' );
            $this->api_key       = $this->get_option( 'api_key' );
            $this->payment_method = $this->get_option( 'payment_method', 'bKash' );
            $this->supabase_url   = $this->get_option( 'supabase_url' );
            $this->anon_key       = $this->get_option( 'anon_key' );
            $this->merchant_name  = $this->get_option( 'merchant_name' );
            $this->secret_key     = $this->get_option( 'secret_key' );
            $this->widget_url     = $this->get_option( 'widget_url' );

            // Actions
            add_action( 'woocommerce_update_options_payment_gateways_' . $this->id, array( $this, 'process_admin_options' ) );
            
            // Webhook Hook
            add_action( 'woocommerce_api_wc_swapnopay_gateway', array( $this, 'check_webhook_response' ) );
        }

        // Configuration Form Fields
        public function init_form_fields() {
            $this->form_fields = array(
                'enabled' => array(
                    'title'   => __( 'Enable/Disable', 'woocommerce-swapnopay' ),
                    'type'    => 'checkbox',
                    'label'   => __( 'Enable SwapnoPay Checkout', 'woocommerce-swapnopay' ),
                    'default' => 'no'
                ),
                'title' => array(
                    'title'       => __( 'Title', 'woocommerce-swapnopay' ),
                    'type'        => 'text',
                    'description' => __( 'This controls the title which the user sees during checkout.', 'woocommerce-swapnopay' ),
                    'default'     => __( 'bKash/Nagad/Rocket (SwapnoPay)', 'woocommerce-swapnopay' ),
                    'desc_tip'    => true,
                ),
                'description' => array(
                    'title'       => __( 'Description', 'woocommerce-swapnopay' ),
                    'type'        => 'textarea',
                    'description' => __( 'This controls the description which the user sees during checkout.', 'woocommerce-swapnopay' ),
                    'default'     => __( 'Pay securely using mobile banking MFS. Your payment is verified automatically in real-time.', 'woocommerce-swapnopay' ),
                ),
                'merchant_id' => array(
                    'title'       => __( 'SwapnoPay Merchant ID', 'woocommerce-swapnopay' ),
                    'type'        => 'text',
                    'description' => __( 'Your merchant ID from the SwapnoPay merchant account.', 'woocommerce-swapnopay' ),
                    'desc_tip'    => true,
                ),
                'api_key' => array(
                    'title'       => __( 'SwapnoPay API Key', 'woocommerce-swapnopay' ),
                    'type'        => 'password',
                    'description' => __( 'Sent from your web server. Keep this key private.', 'woocommerce-swapnopay' ),
                    'desc_tip'    => true,
                ),
                'payment_method' => array(
                    'title'       => __( 'Default Mobile Payment Method', 'woocommerce-swapnopay' ),
                    'type'        => 'select',
                    'default'     => 'bKash',
                    'options'     => array(
                        'bKash' => __( 'bKash', 'woocommerce-swapnopay' ),
                        'Nagad' => __( 'Nagad', 'woocommerce-swapnopay' ),
                        'Rocket' => __( 'Rocket', 'woocommerce-swapnopay' ),
                        'Upay' => __( 'Upay', 'woocommerce-swapnopay' ),
                    ),
                ),
                'supabase_url' => array(
                    'title'       => __( 'Supabase Project URL', 'woocommerce-swapnopay' ),
                    'type'        => 'text',
                    'description' => __( 'Enter your private self-hosted Supabase URL.', 'woocommerce-swapnopay' ),
                ),
                'anon_key' => array(
                    'title'       => __( 'Supabase Anon Key', 'woocommerce-swapnopay' ),
                    'type'        => 'text',
                    'description' => __( 'Enter your Supabase anonymous API key.', 'woocommerce-swapnopay' ),
                ),
                'merchant_name' => array(
                    'title'       => __( 'Merchant Store Name', 'woocommerce-swapnopay' ),
                    'type'        => 'text',
                    'description' => __( 'This name will be displayed at checkout widget header (e.g. DreamMart).', 'woocommerce-swapnopay' ),
                ),
                'secret_key' => array(
                    'title'       => __( 'Legacy Webhook Secret Key', 'woocommerce-swapnopay' ),
                    'type'        => 'password',
                    'description' => __( 'Kept for older installations. Current callbacks are signed with the API key above.', 'woocommerce-swapnopay' ),
                ),
                'widget_url' => array(
                    'title'       => __( 'SwapnoPay Hosted Widget Location', 'woocommerce-swapnopay' ),
                    'type'        => 'text',
                    'description' => __( 'Absolute URL to the widget folder directory containing widget.html (e.g. https://mystore.com/swapnopay/web/).', 'woocommerce-swapnopay' ),
                )
            );
        }

        // Process Checkouts & Redirect to Widget Frame
        public function process_payment( $order_id ) {
            $order = wc_get_order( $order_id );

            if ( ! $order || empty( $this->merchant_id ) || empty( $this->api_key ) ) {
                wc_add_notice( __( 'SwapnoPay merchant ID and API key must be configured.', 'woocommerce-swapnopay' ), 'error' );
                return;
            }
            $api_base = defined( 'SWAPNOPAY_API_URL' ) ? SWAPNOPAY_API_URL : 'https://api.swapnopay.top';

            $payload = array(
                'merchant_id'    => $this->merchant_id,
                'tran_id'        => 'WC-' . (string) $order_id,
                'order_number'   => (string) $order->get_order_number(),
                'amount'         => (float) $order->get_total(),
                'cus_phone'      => $order->get_billing_phone(),
                'cus_email'      => $order->get_billing_email(),
                'cus_name'       => trim( $order->get_billing_first_name() . ' ' . $order->get_billing_last_name() ),
                'payment_method' => in_array( $this->payment_method, array( 'bKash', 'Nagad', 'Rocket', 'Upay' ), true ) ? $this->payment_method : 'bKash',
                'success_url'    => $this->get_return_url( $order ),
                'callback_url'   => add_query_arg( 'wc-api', 'wc_swapnopay_gateway', home_url( '/' ) ),
            );

            $response = wp_remote_post( rtrim( $api_base, '/' ) . '/v1/payment/create-order', array(
                'method'    => 'POST',
                'headers'   => array(
                    'Content-Type'  => 'application/json',
                    'X-API-Key'     => $this->api_key,
                    'X-Merchant-ID' => $this->merchant_id,
                ),
                'body'      => json_encode( $payload ),
                'timeout'   => 15
            ) );

            if ( is_wp_error( $response ) ) {
                wc_add_notice( 'Connection to payment gateway failed. Please try again.', 'error' );
                return;
            }

            $body = json_decode( wp_remote_retrieve_body( $response ), true );

            if ( wp_remote_retrieve_response_code( $response ) < 200 || wp_remote_retrieve_response_code( $response ) >= 300 || empty( $body['checkout_url'] ) ) {
                wc_add_notice( 'Payment gateway rejected order registration: ' . ( isset( $body['error'] ) ? $body['error'] : 'Unknown Error' ), 'error' );
                return;
            }

            // The gateway owns checkout-session creation and verification. Redirect to
            // its signed, server-generated URL; never expose merchant database keys in
            // a browser URL or construct a client-controlled payment session here.
            $redirect_url = esc_url_raw( $body['checkout_url'] );
            $redirect_parts = wp_parse_url( $redirect_url );
            if ( empty( $redirect_parts['scheme'] ) || 'https' !== strtolower( $redirect_parts['scheme'] ) || empty( $redirect_parts['host'] ) ) {
                wc_add_notice( 'Payment gateway returned an invalid checkout URL.', 'error' );
                return;
            }

            $order->update_status( 'pending', 'Waiting for SwapnoPay payment verification.' );
            $order->save();

            // Return success and redirect url
            return array(
                'result'   => 'success',
                'redirect' => $redirect_url
            );
        }

        // Webhook Handler: process callbacks from process-sms Deno function
        public function check_webhook_response() {
            $signature = isset( $_SERVER['HTTP_X_SWAPNOPAY_SIGNATURE'] ) ? $_SERVER['HTTP_X_SWAPNOPAY_SIGNATURE'] : '';
            $raw_payload = file_get_contents( 'php://input' );
            $data = json_decode( $raw_payload, true );

            if ( ! is_array( $data ) || ! $signature || empty( $this->api_key ) ) {
                status_header( 400 );
                echo 'Bad Request';
                exit;
            }

            // Callback signatures use the merchant API key and exact request body.
            $expected_sig = hash_hmac( 'sha256', $raw_payload, $this->api_key );

            if ( ! hash_equals( $expected_sig, $signature ) ) {
                status_header( 401 );
                echo 'Unauthorized Signature';
                exit;
            }

            // Signature is valid. Update order status
            if ( empty( $data['merchant_id'] ) || ! hash_equals( (string) $this->merchant_id, (string) $data['merchant_id'] ) ) {
                status_header( 403 );
                echo 'Merchant Mismatch';
                exit;
            }
            if ( empty( $data['tran_id'] ) || ! preg_match( '/^WC-(\d+)$/', (string) $data['tran_id'], $matches ) ) {
                status_header( 400 );
                echo 'Invalid Order Reference';
                exit;
            }
            $order_id = absint( $matches[1] );
            $status = strtoupper( (string) ( $data['status'] ?? '' ) );
            $order = wc_get_order( $order_id );

            if ( ! $order ) {
                status_header( 404 );
                echo 'Order Not Found';
                exit;
            }

            if ( abs( (float) $order->get_total() - (float) ( $data['amount'] ?? 0 ) ) > 0.01 ) {
                status_header( 409 );
                echo 'Amount Mismatch';
                exit;
            }

            if ( $status === 'PAID' && ! $order->is_paid() ) {
                $trx_id = sanitize_text_field( (string) ( $data['trx_id'] ?? '' ) );
                $order->payment_complete( $trx_id );
                $order->add_order_note( sprintf( 'Payment verified by SwapnoPay (TrxID: %s).', $trx_id ) );
                status_header( 200 );
                echo 'Success';
                exit;
            }

            status_header( 200 );
            echo 'Ignored';
            exit;
        }

    }
}

// Add gateway to WooCommerce selection list
add_filter( 'woocommerce_payment_gateways', 'add_swapnopay_gateway' );
function add_swapnopay_gateway( $gateways ) {
    $gateways[] = 'WC_Gateway_SwapnoPay';
    return $gateways;
}
