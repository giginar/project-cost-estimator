# AWS deployment guide (low-cost / Free Tier)

## Architecture

```text
Browser -> Amplify Hosting (Angular)
            | HTTPS API requests
            v
          API Gateway
            v
          Elastic Beanstalk single instance (Spring Boot, port 5000)
            v
          In-memory repositories
```

Elastic Beanstalk itself has no additional service charge. The environment below
uses one EC2 instance and deliberately has **no load balancer**. API Gateway gives
the browser an HTTPS API endpoint and forwards requests to the backend. Do not
select a load-balanced Beanstalk environment for this low-cost setup.

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
zero bill. EC2, its public IPv4 address, storage, API Gateway and Amplify usage
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

## 3. Put API Gateway in front of the backend

Configure an API Gateway proxy integration that forwards the complete request
path, query string, request body, HTTP method, and `Authorization` header to the
Beanstalk environment. Enable CORS for the Amplify hostname and allow
`authorization,content-type` plus all API methods used by the application.

The current production API URL is configured in
`frontend/src/environments/environment.production.ts`. Verify it with:

```text
https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/api-docs
```

## 4. Deploy Angular with Amplify Hosting

1. Open **Amplify > Create new app > Host web app** and connect this repository.
2. Treat it as a normal single application; do not select **My app is a
   monorepo**. Amplify detects the root `amplify.yml`, runs the frontend commands
   from the repository root, and uses Node 22.
3. Deploy the chosen branch.
4. Open **Hosting > Rewrites and redirects > Manage redirects**.
5. Copy the JSON from `deploy/amplify-rewrites.example.json` and save it. This is
   only the Angular SPA fallback; API requests go directly to API Gateway.

The frontend services continue to use `/api/v1/...` internally. During a
production build, the API URL interceptor replaces that prefix with the API
Gateway URL. Local development keeps using the Angular proxy.

Verify these flows from the Amplify URL:

1. Login with a demo account.
2. Load the project list.
3. Create or edit a record.
4. Refresh a nested Angular route directly.

## 5. Stop/delete resources when not needed

An Elastic Beanstalk single-instance environment is intended to be continuously
available. Terminating the environment removes its instance; recreate and
redeploy it later. Disabling/deleting only the Amplify app does not stop EC2.
Delete unused API Gateway APIs/stages, and check for leftover Elastic IPs, EBS
volumes and S3 application-version objects.

## Production follow-up

The next engineering step is replacing `ProjectRepository`, `ResourceRepository`,
`UserRepository`, and `InMemoryAuthenticationTokenStore` with persistent adapters.
For this object-heavy domain model, PostgreSQL is the straightforward option,
but a permanently running RDS instance increases cost. Do this before storing
real project data; do not treat an EC2 instance restart as a backup strategy.
