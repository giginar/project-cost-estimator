# AWS deployment guide (low-cost / Free Tier)

## Architecture

```text
Browser -> Amplify Hosting (Angular)
            | /api/* 200 rewrite
            v
          CloudFront (HTTPS, cache disabled)
            | HTTP origin
            v
          Elastic Beanstalk single instance (Spring Boot, port 5000)
            v
          In-memory repositories
```

Elastic Beanstalk itself has no additional service charge. The environment below
uses one EC2 instance and deliberately has **no load balancer**. CloudFront gives
the backend an HTTPS address, which Amplify requires for an external reverse
proxy. Do not select a load-balanced Beanstalk environment: an Application Load
Balancer is not suitable for this low-cost setup.

> Important: projects, users and authentication tokens are currently stored in
> Java memory. Any restart or replacement of the EC2 instance resets them. This
> deployment is suitable for a demo/POC, not yet for production data.

## 0. Protect the account from surprise charges

1. In **Billing and Cost Management > Budgets**, create a monthly cost budget
   (for example USD 5) with alerts at 50%, 80% and 100%.
2. Enable billing alerts and check **Free Tier** usage regularly.
3. Use one region for all resources. `eu-central-1` is a reasonable nearby
   choice for Turkey; compare latency and pricing if needed.

AWS accounts opened on or after 15 July 2025 use the newer credit-based Free
Tier (free plan up to six months or until credits run out). Older accounts keep
the legacy Free Tier rules. "Free Tier" therefore does not guarantee a permanent
zero bill. EC2, its public IPv4 address, storage, CloudFront and Amplify usage
must all be watched in Billing.

## 1. Build the backend bundle

Requirements: Java 21 and PowerShell.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-eb.ps1
```

This runs the tests and creates:

```text
dist/cost-estimator-backend-eb.zip
```

The bundle contains the executable jar, `Procfile`, and the Elastic Beanstalk
settings. The JVM is capped at 512 MB so it can run on a 1 GiB instance.

## 2. Create the Elastic Beanstalk backend

1. Open **Elastic Beanstalk > Create application**.
2. Application name: `project-cost-estimator`.
3. Environment tier: **Web server environment**.
4. Platform: **Java**, current **Corretto 21** platform branch.
5. Application code: upload `dist/cost-estimator-backend-eb.zip`.
6. Preset/configuration: choose **Single instance**. Never choose load balanced
   for this setup.
7. Instance type: select a Free-Tier-eligible small instance shown by your own
   account (commonly `t3.micro`; eligibility varies by account/region/program).
8. Root volume: `gp3`, 8 GiB is sufficient for this demo.
9. Create the environment and wait until health is green.

Test the URL shown by Beanstalk:

```text
http://YOUR-ENV.REGION.elasticbeanstalk.com/actuator/health
```

It should return JSON containing `"status":"UP"`.

The checked-in environment settings intentionally enable demo data and disable
real email delivery. The demo accounts are visible in the login screen/source
and are not safe production credentials. Before real use, persistence, initial
admin provisioning, secret management and email verification must be completed.

For later backend releases, run the packaging script again and use
**Elastic Beanstalk > Upload and deploy** with the new zip.

## 3. Put CloudFront in front of the backend

Amplify reverse proxy targets must use HTTPS, while a single-instance Beanstalk
environment exposes HTTP. Create one CloudFront distribution:

1. **Origin domain**: enter the Beanstalk hostname only (without `http://`).
2. **Protocol to origin**: HTTP only; port 80.
3. **Viewer protocol policy**: Redirect HTTP to HTTPS.
4. **Allowed HTTP methods**: GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE.
5. **Cache policy**: managed `CachingDisabled` (API/auth responses must not be
   cached).
6. **Origin request policy**: managed `AllViewerExceptHostHeader`. This forwards
   query strings and the `Authorization` header while allowing the Beanstalk
   origin host to be used.
7. Do not enable WAF for this demo; it adds cost.

After deployment, verify:

```text
https://YOUR_DISTRIBUTION.cloudfront.net/actuator/health
```

## 4. Deploy Angular with Amplify Hosting

1. Open **Amplify > Create new app > Host web app** and connect this repository.
2. Treat it as a normal single application; do not select **My app is a
   monorepo**. Amplify detects the root `amplify.yml`, runs the frontend commands
   from the repository root, and uses Node 22.
3. Deploy the chosen branch.
4. Open **Hosting > Rewrites and redirects > Manage redirects**.
5. Copy the JSON from `deploy/amplify-rewrites.example.json`, replace
   `REPLACE_WITH_CLOUDFRONT_DOMAIN` with the CloudFront domain, and save.

Rule order matters: `/api/<*>` must be before the Angular SPA fallback. The API
then remains same-origin from the browser's point of view, so no CORS change is
needed and the existing frontend code continues to use `/api/v1/...`.

Verify these flows from the Amplify URL:

1. Login with a demo account.
2. Load the project list.
3. Create or edit a record.
4. Refresh a nested Angular route directly.

## 5. Stop/delete resources when not needed

An Elastic Beanstalk single-instance environment is intended to be continuously
available. Terminating the environment removes its instance; recreate and
redeploy it later. Disabling/deleting only the Amplify app does not stop EC2.
Delete unused CloudFront distributions after disabling them, and check for
leftover Elastic IPs, EBS volumes and S3 application-version objects.

## Production follow-up

The next engineering step is replacing `ProjectRepository`, `ResourceRepository`,
`UserRepository`, and `InMemoryAuthenticationTokenStore` with persistent adapters.
For this object-heavy domain model, PostgreSQL is the straightforward option,
but a permanently running RDS instance increases cost. Do this before storing
real project data; do not treat an EC2 instance restart as a backup strategy.
