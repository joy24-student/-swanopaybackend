import React, { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error, errorInfo: null };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[ErrorBoundary caught error]', error, errorInfo);
    this.setState({ errorInfo });
  }

  public handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    window.location.reload();
  };

  public handleGoHome = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    window.location.href = '/dashboard';
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 20,
            background: 'var(--bg-app, #F8FAFC)',
            color: 'var(--text-primary, #0F172A)',
            fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
          }}
        >
          <div
            style={{
              maxWidth: 580,
              width: '100%',
              background: 'var(--bg-surface, #FFFFFF)',
              border: '1px solid var(--border-default, #E2E8F0)',
              borderRadius: 16,
              padding: 32,
              boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1)',
              textAlign: 'center',
            }}
          >
            <div
              style={{
                width: 56,
                height: 56,
                borderRadius: '50%',
                background: '#FEE2E2',
                color: '#EF4444',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px',
              }}
            >
              <AlertTriangle size={28} />
            </div>
            <h2 style={{ fontSize: 20, fontWeight: 700, margin: '0 0 8px' }}>
              Something went wrong
            </h2>
            <p style={{ fontSize: 13, color: 'var(--text-secondary, #64748B)', margin: '0 0 20px', lineHeight: 1.5 }}>
              An unexpected error occurred while rendering this page. You can reload the page or return to the overview dashboard.
            </p>

            {this.state.error && (
              <div
                style={{
                  textAlign: 'left',
                  background: 'var(--bg-subtle, #F1F5F9)',
                  border: '1px solid var(--border-default, #E2E8F0)',
                  borderRadius: 8,
                  padding: 12,
                  fontSize: 12,
                  color: '#DC2626',
                  fontFamily: 'monospace',
                  marginBottom: 24,
                  wordBreak: 'break-word',
                  maxHeight: 140,
                  overflowY: 'auto',
                }}
              >
                <strong>{this.state.error.name}:</strong> {this.state.error.message}
              </div>
            )}

            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button
                type="button"
                onClick={this.handleReset}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '10px 20px',
                  borderRadius: 8,
                  border: 'none',
                  background: 'var(--brand-primary, #4338CA)',
                  color: '#FFFFFF',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                <RefreshCw size={15} />
                Reload Page
              </button>
              <button
                type="button"
                onClick={this.handleGoHome}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '10px 20px',
                  borderRadius: 8,
                  border: '1px solid var(--border-default, #CBD5E1)',
                  background: 'transparent',
                  color: 'var(--text-primary, #0F172A)',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                <Home size={15} />
                Go to Dashboard
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
