# Research: EA API Update 2026

Based on the provided Lutris source analysis:

## Authentication Flow
1. **Initial Login**:
   - `client_id`: `ORIGIN_SPA_ID`
   - `display`: `originXWeb/login`
   - `redirect_uri`: `https://www.origin.com/views/login.html`
2. **Silent Token Recovery**:
   - `client_id`: `ORIGIN_JS_SDK`
   - `response_type`: `token`
   - `redirect_uri`: `nucleus:rest`
   - `prompt`: `none`

## Data Fetching
- **Identity Endpoint**: `https://gateway.ea.com/proxy/identity/pids/me`
- **Game List Endpoint**: `https://service-aggregation-layer.juno.ea.com/graphql`
- **Headers**:
  ```json
  {
    "Authorization": "Bearer <token>",
    "AuthToken": "<token>",
    "X-AuthToken": "<token>"
  }
  ```
- **GraphQL Query**:
  ```graphql
  query getEntitlements($limit: Int, $next: String) {
    me {
      ownedGameProducts(
        locale: "DEFAULT", entitlementEnabled: true,
        storefronts: [EA], type: [DIGITAL_FULL_GAME, PACKAGED_FULL_GAME],
        platforms: [PC], paging: { limit: $limit, next: $next }
      ) {
        next,
        items {
          originOfferId
          product {
            name
            baseItem { gameType }
          }
        }
      }
    }
  }
  ```
