import { type NextRequest, NextResponse } from 'next/server';

// Auth state lives in sessionStorage (access token) and an HttpOnly cookie (refresh token
// restricted to /api/auth/refresh). Neither is reliably readable in middleware.
// Route protection is handled client-side via AuthGuard components in protected layouts.
// This middleware handles cross-cutting concerns only.

export function middleware(_request: NextRequest) {
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
