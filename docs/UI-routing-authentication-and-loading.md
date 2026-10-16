# UI Routing, Authentication, and Loading

This document explains the Angular UI code discussed in this project: account loading, route guards, the HTTP interceptor, and eager and lazy loading.

## Account Loading

The account service loads the logged-in user's accounts:

```typescript
return this.accountApi.getMyAccounts().pipe(
  tap(accounts => {
    this.accounts.set(accounts);
    this.currentAccountId.set(accounts[0]?.accountId ?? null);
  }),
  map(() => undefined),
  catchError(() => {
    this.accounts.set([]);
    this.currentAccountId.set(null);
    return of(undefined);
  }),
);
```

The code works as follows:

- `getMyAccounts()` calls the trading API.
- `tap(...)` performs side effects without changing the response. It stores the accounts in the `accounts` signal and selects the first account.
- `accounts[0]?.accountId` safely reads the first account. If there are no accounts, `null` is selected.
- `map(() => undefined)` changes the successful result into `Observable<void>` because callers only need to know that loading finished.
- `catchError(...)` handles a failed request. It clears the account state and returns `of(undefined)`, so the observable completes without passing an error to the caller.

## Routes

The route table is defined in `ui/src/app/app.routes.ts`.

```typescript
{ path: '', pathMatch: 'full', redirectTo: 'dashboard' }
```

The root URL `/` redirects to `/dashboard`. `pathMatch: 'full'` means the redirect applies only when the complete URL is empty.

```typescript
{
  path: 'login',
  canActivate: [guestGuard],
  loadComponent: () => import('./features/login/login.component')
    .then(m => m.LoginComponent),
}
```

This defines the login page. The `guestGuard` runs before navigation. The page is loaded lazily, meaning its code is downloaded when `/login` is visited.

```typescript
{
  path: '',
  canActivate: [authGuard],
  loadComponent: () => import('./features/shell/shell.component')
    .then(m => m.ShellComponent),
  children: [
    // dashboard, trade, orders, and accounts
  ]
}
```

This protected parent route contains the main application shell. Its child routes are:

- `/dashboard`
- `/trade`
- `/orders`
- `/accounts`

All of these routes require authentication because they are children of the route protected by `authGuard`.

```typescript
{ path: '**', component: NotFoundComponent }
```

The wildcard route catches URLs that do not match any earlier route and displays the 404 page.

## Route Guards

A route guard controls whether Angular allows navigation to a route. It does not protect an API request by itself.

### `authGuard`

The authentication guard checks `AuthService.isAuthenticated()`.

```typescript
if (auth.isAuthenticated()) {
  return true;
}
```

Returning `true` allows navigation.

If the user is not authenticated, the guard creates a redirect to login:

```typescript
return router.createUrlTree(['/login'], {
  queryParams: { returnUrl }
});
```

For example, trying to open `/orders` while logged out can send the user to:

```text
/login?returnUrl=/orders
```

The `returnUrl` remembers the page the user originally requested.

The application calls `restoreSession()` during startup. If a refresh token exists, the app attempts to restore the access token before the first navigation.

### `guestGuard`

The guest guard protects pages intended for logged-out users, such as login:

```typescript
return auth.isAuthenticated()
  ? router.createUrlTree(['/dashboard'])
  : true;
```

- Logged out: allow `/login`.
- Logged in: redirect to `/dashboard`.

## HTTP Interceptor

In general, an HTTP interceptor is code that automatically runs when an application sends or receives an HTTP request. It acts as a central checkpoint where requests and responses can be inspected or changed. Interceptors are useful for applying behavior consistently, such as adding authentication headers, logging traffic, handling common errors, or retrying failed requests, without repeating that logic in every service.

The interceptor is registered globally in `ui/src/app/app.config.ts`:

```typescript
provideHttpClient(withInterceptors([authInterceptor]))
```

An HTTP interceptor runs around Angular HTTP requests and responses.

### Adding the access token

The interceptor first checks whether the request targets the trading API:

```typescript
if (!isApiRequest(req.url)) {
  return next(req);
}
```

Non-trading requests pass through unchanged. This prevents the access token from being sent to the authentication service or third-party hosts.

For trading API requests, the interceptor clones the immutable request and adds:

```http
Authorization: Bearer <access-token>
```

### Refreshing an expired token

If the API returns HTTP `401 Unauthorized`, the interceptor calls `auth.refresh()` and retries the original request with the new access token.

Only the original request is refreshed. If the retry also returns `401`, it is passed to the caller instead of being refreshed repeatedly. This prevents an infinite retry loop.

If refreshing fails, the interceptor calls `auth.logout()`. The session is cleared and the user is redirected to `/login`.

## Eager and Lazy Loading

### Eager loading

Eagerly loaded code is imported when the application starts.

Examples in this UI include:

```typescript
import { NotFoundComponent } from './features/not-found/not-found.component';
```

The route configuration, guards, interceptor, and shared services are also part of the initial application code because they are imported or registered during startup.

### Lazy loading

Lazy-loaded code is downloaded only when it is needed.

The feature pages use Angular's `loadComponent()`:

```typescript
loadComponent: () =>
  import('./features/dashboard/dashboard.component')
    .then(m => m.DashboardComponent)
```

Lazy loading can reduce the initial bundle size and improve startup time. The tradeoff is that the first visit to a lazy route may require a small additional download.

The current 404 component is eagerly loaded because it is directly imported and used as `component: NotFoundComponent`.

## Summary

- Route guards decide whether a user may navigate to a page.
- The HTTP interceptor adds JWT authentication to trading API requests and retries once after a `401` response.
- Eager loading happens during application startup.
- Lazy loading happens when a route or feature is first used.