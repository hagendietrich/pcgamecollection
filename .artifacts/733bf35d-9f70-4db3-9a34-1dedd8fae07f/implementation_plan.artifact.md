# Fix Ubisoft Sync (Consolidated Identity & Domain Fix)

The login is currently failing with a 403 error (likely due to the aggressive `emptySet()` header suppression) and the library fetch is failing with 401/404 errors (due to domain and identity discrepancies). I will revert the header suppression to a specific origin set and consolidate all API calls onto the verified public domain using the verified AppID.

## User Review Required

> [!IMPORTANT]
> **Identity Alignment**: I am standardizing the app to use `f35adcb5-1911-440c-b1c9-48fdc1701c68` for **all** Ubisoft requests (Login, Aggregation, and Entitlements). This matches the successful background traffic observed on your device.
>
> **Domain Consolidation**: I am removing all references to `api-ubiservices.ubisoft.com` and using `public-ubiservices.ubi.com` exclusively to avoid authorization drops between domains.

## Proposed Changes

### UI Components

#### [MODIFY] [UbisoftAuthWebView.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/ui/components/UbisoftAuthWebView.kt)
- **Header Allow-List**: Change `emptySet()` to an explicit list of origins (`connect.ubisoft.com`, `public-ubiservices.ubi.com`, `google.com`). This is more reliable for suppressing the `X-Requested-With` header on certain WebView versions.
- **Identity Sync**: Keep `f35adcb5-1911-440c-b1c9-48fdc1701c68` as the primary AppID.

### Data Layer

#### [MODIFY] [UbisoftClient.kt](file:///var/home/hagen/Coding/AndroidStudio/DigitalCollectionManager/app/src/main/java/com/example/digitalcollectionmanager/data/api/UbisoftClient.kt)
- **AppID Sync**: Set `APP_ID` to `f35adcb5-1911-440c-b1c9-48fdc1701c68`.
- **Domain Update**: Set `API_BASE_URL` to `https://public-ubiservices.ubi.com`.
- **Header Refinement**: Ensure `Ubi-AppId` and `Ubi-SessionId` are sent with every library request.

## Verification Plan

### Manual Verification
1. Run the app and connect to Ubisoft.
2. Verify that the 403 error is gone and login succeeds.
3. Verify that the library sync no longer returns 401 or 404 and successfully imports games.
