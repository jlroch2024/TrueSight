# The Live Site

TrueSight runs on Google Cloud, in Singapore (`asia-southeast1`), in the project `truesight-509715`. This page says
what runs where, how a merge reaches the live site, and every step used to set it up, so anyone can rebuild it.

## What Runs Where

| Piece | Google Cloud name | What it does |
|---|---|---|
| The app | Cloud Run service `truesight` | Runs the one Docker image (`Dockerfile`): the backend, which also serves the website |
| The database | Cloud SQL instance `truesight-db`, database `truesight`, user `truesight` | PostgreSQL 16. The app reaches it through Cloud Run's Cloud SQL connection, never over the open internet |
| Secrets | Secret Manager: `DATABASE_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY` | Handed to the app as environment variables when it starts. They never appear in this repository |
| The app's identity | Service account `truesight-app` | Allowed only to connect to the database and read the secrets |
| The deployer's identity | Service account `github-deployer` | Used by GitHub Actions to deploy. Its key is in GitHub's encrypted secrets as `GCP_SA_KEY` |
| Spending alert | Billing, Budgets & alerts: `TrueSight` | Emails at 50%, 90% and 100% of the monthly budget |

Everything else the app needs is a plain environment variable on the Cloud Run service, not a secret:
`SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL`, `DATABASE_USERNAME`, `GEMINI_MODEL` and `SEC_USER_AGENT`.

## How a Merge Reaches the Live Site

1. A pull request is merged into `main`.
2. The **CI** workflow runs every test, exactly as on the pull request.
3. Only if CI passes, the **Deploy** workflow (`.github/workflows/deploy.yml`) starts. It uploads the code to Google
   Cloud, which builds the image from the `Dockerfile` and starts it.
4. The old version keeps answering until the new one is ready, so the site never goes down. The whole trip usually
   takes about 10 minutes.

If CI fails, nothing is deployed, and the live site keeps the last working version. Every deploy keeps the settings
and secrets already on the service, so the workflow never needs to know them.

## Everyday Tasks

The commands below are for PowerShell, where a backtick (`` ` ``) at the end of a line continues the command on the
next line. They need the Google Cloud CLI, logged in with `gcloud auth login`.

| Task | How |
|---|---|
| See what went wrong on the live site | Cloud Run, `truesight`, **Logs**. Or `gcloud run services logs read truesight --region asia-southeast1` |
| Watch a deploy | GitHub, **Actions**, **Deploy** |
| Go back to the previous version | Cloud Run, `truesight`, **Revisions**, pick the last working one, **Manage traffic**, send it 100% |
| Pause the database to save money (the site stops working) | `gcloud sql instances patch truesight-db --activation-policy=NEVER` |
| Start it again | `gcloud sql instances patch truesight-db --activation-policy=ALWAYS` |
| Change a secret, e.g. a new Gemini key | Secret Manager, the secret, **New version**. Then redeploy (merge anything, or re-run the last Deploy) so the app reads it |

## Setting It Up From Nothing

This was done once, by hand. It never needs doing again unless the project is rebuilt.

### 1. Project, Billing and Spending Alert

1. In the Google Cloud website, create a project and link it to the billing account that holds the credits.
2. **Before anything costs money:** Billing, **Budgets & alerts**, **Create budget**. Name `TrueSight`, only this
   project, a monthly amount, alerts at 50%, 90% and 100%, emailed to billing admins. A budget **warns**; it does not
   stop spending.
3. Install the Google Cloud CLI, then:

   ```powershell
   gcloud auth login
   gcloud config set project truesight-509715
   gcloud config set run/region asia-southeast1
   ```

### 2. Services and Database

```powershell
gcloud services enable run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com `
  artifactregistry.googleapis.com cloudbuild.googleapis.com

gcloud sql instances create truesight-db --database-version=POSTGRES_16 --edition=ENTERPRISE `
  --tier=db-f1-micro --region=asia-southeast1 --storage-size=10GB --availability-type=zonal
gcloud sql databases create truesight --instance=truesight-db
```

Then make a long random password, create the database user with it, and save it as a secret, without it ever
appearing on screen or in a file that stays behind:

```powershell
$dbPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
gcloud sql users create truesight --instance=truesight-db --password=$dbPassword
```

### 3. Secrets

Run this in the same PowerShell window as step 2, which still holds `$dbPassword`. Each secret is written to a
temporary file, stored, and the file deleted. `DATABASE_PASSWORD` is the password from step 2, `JWT_SECRET` a new long random value, and `GEMINI_API_KEY` the team's Gemini key.

```powershell
$jwtSecret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$geminiKey = Read-Host "Gemini API key"
foreach ($pair in @(@("DATABASE_PASSWORD", $dbPassword), @("JWT_SECRET", $jwtSecret), @("GEMINI_API_KEY", $geminiKey))) {
  $name = $pair[0]; $f = New-TemporaryFile
  [IO.File]::WriteAllText($f, $pair[1])
  gcloud secrets create $name --data-file=$f; Remove-Item $f
}
gcloud secrets list
```

### 4. The App's Identity

The app runs as its own service account, allowed only what it needs: connect to the database and read the secrets.

```powershell
gcloud iam service-accounts create truesight-app --display-name="TrueSight app"
$app = "serviceAccount:truesight-app@truesight-509715.iam.gserviceaccount.com"
gcloud projects add-iam-policy-binding truesight-509715 --member=$app --role=roles/cloudsql.client
gcloud projects add-iam-policy-binding truesight-509715 --member=$app --role=roles/secretmanager.secretAccessor
```

Google Cloud builds the image using the project's default compute service account, which needs permission to build
for Cloud Run:

```powershell
$number = gcloud projects describe truesight-509715 --format="value(projectNumber)"
gcloud projects add-iam-policy-binding truesight-509715 `
  --member="serviceAccount:$number-compute@developer.gserviceaccount.com" --role=roles/run.builder
```

### 5. The First Deploy

Run this in the repository root. It uploads the code (never `.env`: see `.gcloudignore`), builds the image, and
starts the service with its settings and secrets. Every later deploy keeps them.

```powershell
gcloud run deploy truesight --source . --region asia-southeast1 --allow-unauthenticated `
  --service-account=truesight-app@truesight-509715.iam.gserviceaccount.com `
  --add-cloudsql-instances=truesight-509715:asia-southeast1:truesight-db `
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,DATABASE_URL=jdbc:postgresql:///truesight?cloudSqlInstance=truesight-509715:asia-southeast1:truesight-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory,DATABASE_USERNAME=truesight,GEMINI_MODEL=gemini-3.8-flash,SEC_USER_AGENT=TrueSight contact@truesight.com" `
  --set-secrets="DATABASE_PASSWORD=DATABASE_PASSWORD:latest,JWT_SECRET=JWT_SECRET:latest,GEMINI_API_KEY=GEMINI_API_KEY:latest" `
  --memory=1Gi --max-instances=3
```

- `--allow-unauthenticated` lets anyone open the site. TrueSight's own log-in protects the data.
- `DATABASE_URL` names Google's Cloud SQL connector (in `pom.xml`), which reaches the database through Cloud Run's
  Cloud SQL connection using the app's identity. It holds no password.
- `--max-instances=3` keeps the small database from running out of connections, and caps the cost.

It prints the live link when it finishes. Open it, sign up and log in, and check `/swagger-ui.html`.

### 6. Automatic Deploys From GitHub

GitHub Actions deploys as its own service account, allowed only to deploy:

```powershell
gcloud iam service-accounts create github-deployer --display-name="GitHub Actions deployer"
$deployer = "serviceAccount:github-deployer@truesight-509715.iam.gserviceaccount.com"
foreach ($role in @("roles/run.sourceDeveloper", "roles/iam.serviceAccountUser",
                    "roles/serviceusage.serviceUsageConsumer", "roles/logging.viewer")) {
  gcloud projects add-iam-policy-binding truesight-509715 --member=$deployer --role=$role
}
```

Then give GitHub its key, and delete the file straight away:

```powershell
gcloud iam service-accounts keys create key.json `
  --iam-account=github-deployer@truesight-509715.iam.gserviceaccount.com
```

1. On GitHub: the repository, **Settings**, **Secrets and variables**, **Actions**, **New repository secret**.
2. Name `GCP_SA_KEY`. Paste the whole of `key.json` as the value. **Add secret**.
3. Delete `key.json`: `Remove-Item key.json`. Never commit it. Anyone holding it can deploy.

From then on, every merge into `main` that passes CI updates the live site.
