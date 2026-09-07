-- V017: Extensive skill taxonomy seed + system prompt templates
-- ─────────────────────────────────────────────────────────────────────

-- 1. Schema: make prompt_templates.user_id nullable + add is_system flag
-- ─────────────────────────────────────────────────────────────────────
ALTER TABLE prompt_templates ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE prompt_templates ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_prompt_templates_is_system ON prompt_templates(is_system) WHERE is_system = TRUE;


-- 2. System prompt templates (user_id = NULL, is_system = TRUE)
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO prompt_templates (user_id, name, category, description, system_prompt, user_prompt, is_public, is_system, version_number)
VALUES

(NULL, 'Ansøgning — Dansk', 'COVER_LETTER',
 'Standard dansk ansøgningsbrev tilpasset jobopslaget.',
 'Du er en erfaren karriererådgiver og professionel tekstforfatter med speciale i danske jobansøgninger. Skriv overbevisende, autentiske tekster der afspejler kandidatens individuelle stemme og styrker. Skriv altid på dansk medmindre andet er angivet.',
 $prompt$Skriv et professionelt ansøgningsbrev baseret på CV'et og jobopslaget nedenfor.

Retningslinjer:
- Åbn med en fængende indledning der viser ægte interesse for rollen og virksomheden
- Kobl 2-3 konkrete erfaringer direkte til de vigtigste krav i jobopslaget
- Fremhæv målbare resultater frem for generelle påstande
- Afslut med et klart call-to-action (inviter til samtale)
- Hold det under 350 ord
- Professionel men personlig tone — undgå klichéer som "jeg er teamplayer"$prompt$,
 TRUE, TRUE, 1),

(NULL, 'Application — English', 'COVER_LETTER',
 'Standard English cover letter tailored to the job posting.',
 'You are an experienced career coach and professional writer specializing in job applications. Write compelling, authentic cover letters that reflect the candidate''s individual voice and strengths.',
 $prompt$Write a professional cover letter based on the CV and job description below.

Guidelines:
- Open with a compelling hook that shows genuine interest in this specific role and company
- Connect 2-3 specific experiences directly to the most important job requirements
- Highlight measurable achievements rather than generic claims
- Close with a clear call-to-action (invite to interview)
- Keep it under 350 words
- Professional yet personal tone — avoid clichés like "I am a team player"$prompt$,
 TRUE, TRUE, 1),

(NULL, 'Ansøgningstekst — Kompakt', 'APPLICATION',
 'Kort, direkte ansøgningstekst til online ansøgningsskemaer (max 200 ord).',
 'Du er karriererådgiver. Skriv kompakte, præcise ansøgningstekster egnet til online skemaer og tekstbokse. Skriv på dansk.',
 $prompt$Skriv en kompakt ansøgningstekst (max 200 ord) til et online ansøgningsskema.

Struktur:
1. Hvem du er + din vigtigste kompetence (1 sætning)
2. Hvorfor netop denne virksomhed/rolle interesserer dig (1-2 specifikke grunde)
3. Ét konkret eksempel på relevant erfaring med et målbart resultat
4. Hvad du kan bidrage med — afslut med invitation til dialog$prompt$,
 TRUE, TRUE, 1),

(NULL, 'Recruiter Message — LinkedIn', 'RECRUITER_MESSAGE',
 'Kort, personlig LinkedIn-besked til recruiter eller hiring manager (max 120 ord).',
 'You are a career coach. Write concise, personalized outreach messages. Be direct, specific and professional. Avoid generic openers.',
 $prompt$Write a brief LinkedIn message (max 120 words) to the recruiter or hiring manager for this position.

Structure:
- Personal opener referencing something specific about the role or company (not "I hope this message finds you well")
- One sentence on why you are a strong fit (specific skill or experience + relevant result)
- Clear ask (e.g. brief call, happy to share application)
- Professional closing$prompt$,
 TRUE, TRUE, 1),

(NULL, 'Follow-up — After Application', 'GENERAL',
 'Høflig opfølgning 1-2 uger efter indsendt ansøgning.',
 'You are a career coach. Write polite, professional follow-up messages that are brief and to the point.',
 $prompt$Write a polite follow-up message to send 1-2 weeks after submitting an application for this role.

Requirements:
- Reference the specific position and approximate date of application
- Express continued genuine interest (one concrete reason)
- Ask politely about the status of the recruitment process
- Max 80 words
- Warm but professional tone$prompt$,
 TRUE, TRUE, 1),

(NULL, 'CV — Vinklet til jobopslag', 'APPLICATION',
 'Tilpas og omstrukturer master-CV''et til at matche et specifikt jobopslag optimalt.',
 'Du er CV-specialist. Tilpas master-CV''et til at fremhæve de mest relevante erfaringer for det specifikke job. Bevar præcist alle fakta — opfind ikke erfaring eller resultater.',
 $prompt$Omskriv CV'et nedenfor så det er optimalt målrettet det vedlagte jobopslag.

Retningslinjer:
- Omstrukturer rækkefølgen af erfaringer og kompetencer så de mest relevante fremhæves øverst
- Inkorporer nøgleord fra jobopslaget naturligt i teksten
- Kvantificér resultater med tal hvor muligt
- Fjern eller nedprioritér irrelevante afsnit
- Formatér som struktureret plaintext med klare sektionsoverskrifter
- Bevar 100 % af de faktuelle informationer — opfind aldrig ny erfaring$prompt$,
 TRUE, TRUE, 1),

(NULL, 'Analyse — CV vs. Jobopslag', 'CV_ANALYSIS',
 'Sammenlign CV med jobopslag og giv konkrete forbedringsforslag.',
 'You are an expert ATS specialist and career coach. Analyze CVs against job postings and provide specific, actionable feedback.',
 $prompt$Analyze the CV against the job posting below and provide structured feedback.

Return your analysis in these sections:
1. **Match score** (0-100) with brief justification
2. **Keyword gaps** — important keywords from the job posting missing from the CV
3. **Strengths** — where the CV aligns well with the requirements
4. **Gaps** — experience or skills required but absent or underrepresented
5. **Top 3 improvements** — specific, actionable changes to improve the match$prompt$,
 TRUE, TRUE, 1);


-- 3. Additional skill taxonomy entries
-- ─────────────────────────────────────────────────────────────────────
-- Uses ON CONFLICT DO NOTHING to be safe against re-runs or overlapping seeds
INSERT INTO skill_taxonomy (name, normalized_name, category) VALUES

-- Languages (additions to existing: Java, Python, JS, TS, Go, Rust, C#, C++, PHP, Ruby, Swift, Kotlin, Scala)
('R',              'r',              'Language'),
('SQL',            'sql',            'Language'),
('HTML',           'html',           'Language'),
('CSS',            'css',            'Language'),
('Bash',           'bash',           'Language'),
('Shell Scripting','shell scripting','Language'),
('Dart',           'dart',           'Language'),
('Elixir',         'elixir',         'Language'),
('Erlang',         'erlang',         'Language'),
('Clojure',        'clojure',        'Language'),
('Haskell',        'haskell',        'Language'),
('Lua',            'lua',            'Language'),
('Julia',          'julia',          'Language'),
('MATLAB',         'matlab',         'Language'),
('Groovy',         'groovy',         'Language'),
('Perl',           'perl',           'Language'),
('F#',             'f#',             'Language'),
('Objective-C',    'objective-c',    'Language'),
('WebAssembly',    'webassembly',    'Language'),
('COBOL',          'cobol',          'Language'),
('PL/SQL',         'pl/sql',         'Language'),
('ABAP',           'abap',           'Language'),
('Solidity',       'solidity',       'Language'),

-- Frameworks (additions)
('Next.js',        'next.js',        'Framework'),
('Nuxt.js',        'nuxt.js',        'Framework'),
('Svelte',         'svelte',         'Framework'),
('SvelteKit',      'sveltekit',      'Framework'),
('NestJS',         'nestjs',         'Framework'),
('Flask',          'flask',          'Framework'),
('Ruby on Rails',  'ruby on rails',  'Framework'),
('Phoenix',        'phoenix',        'Framework'),
('Gin',            'gin',            'Framework'),
('Fiber',          'fiber',          'Framework'),
('Actix',          'actix',          'Framework'),
('Axum',           'axum',           'Framework'),
('Quarkus',        'quarkus',        'Framework'),
('Micronaut',      'micronaut',      'Framework'),
('Ktor',           'ktor',           'Framework'),
('Hibernate',      'hibernate',      'Framework'),
('React Native',   'react native',   'Framework'),
('Flutter',        'flutter',        'Framework'),
('Electron',       'electron',       'Framework'),
('Astro',          'astro',          'Framework'),
('Remix',          'remix',          'Framework'),
('Tailwind CSS',   'tailwind css',   'Framework'),
('Bootstrap',      'bootstrap',      'Framework'),
('tRPC',           'trpc',           'Framework'),
('Hono',           'hono',           'Framework'),
('Expo',           'expo',           'Framework'),
('SolidJS',        'solidjs',        'Framework'),
('Qwik',           'qwik',           'Framework'),

-- Libraries
('NumPy',          'numpy',          'Library'),
('Pandas',         'pandas',         'Library'),
('Matplotlib',     'matplotlib',     'Library'),
('Scikit-learn',   'scikit-learn',   'Library'),
('TensorFlow',     'tensorflow',     'Library'),
('PyTorch',        'pytorch',        'Library'),
('Keras',          'keras',          'Library'),
('Hugging Face',   'hugging face',   'Library'),
('LangChain',      'langchain',      'Library'),
('OpenCV',         'opencv',         'Library'),
('spaCy',          'spacy',          'Library'),
('NLTK',           'nltk',           'Library'),
('SQLAlchemy',     'sqlalchemy',     'Library'),
('Prisma',         'prisma',         'Library'),
('TypeORM',        'typeorm',        'Library'),
('Sequelize',      'sequelize',      'Library'),
('Mongoose',       'mongoose',       'Library'),
('Axios',          'axios',          'Library'),
('Redux',          'redux',          'Library'),
('Zustand',        'zustand',        'Library'),
('Pinia',          'pinia',          'Library'),
('RxJS',           'rxjs',           'Library'),
('Jest',           'jest',           'Library'),
('Vitest',         'vitest',         'Library'),
('Cypress',        'cypress',        'Library'),
('Playwright',     'playwright',     'Library'),
('Puppeteer',      'puppeteer',      'Library'),
('Storybook',      'storybook',      'Library'),
('Celery',         'celery',         'Library'),
('Pydantic',       'pydantic',       'Library'),
('Lombok',         'lombok',         'Library'),
('Jackson',        'jackson',        'Library'),
('gRPC',           'grpc',           'Library'),
('Protocol Buffers','protocol buffers','Library'),
('Tanstack Query', 'tanstack query', 'Library'),
('Zod',            'zod',            'Library'),

-- Databases (additions)
('SQLite',              'sqlite',              'Database'),
('MariaDB',             'mariadb',             'Database'),
('Oracle Database',     'oracle database',     'Database'),
('Microsoft SQL Server','microsoft sql server','Database'),
('DynamoDB',            'dynamodb',            'Database'),
('Apache Cassandra',    'apache cassandra',    'Database'),
('CockroachDB',         'cockroachdb',         'Database'),
('Neo4j',               'neo4j',               'Database'),
('InfluxDB',            'influxdb',            'Database'),
('ClickHouse',          'clickhouse',          'Database'),
('TimescaleDB',         'timescaledb',         'Database'),
('Supabase',            'supabase',            'Database'),
('Firebase',            'firebase',            'Database'),
('Pinecone',            'pinecone',            'Database'),
('Weaviate',            'weaviate',            'Database'),
('Qdrant',              'qdrant',              'Database'),
('pgvector',            'pgvector',            'Database'),
('Apache HBase',        'apache hbase',        'Database'),
('Couchbase',           'couchbase',           'Database'),
('FaunaDB',             'faunadb',             'Database'),

-- Cloud (additions)
('AWS Lambda',         'aws lambda',         'Cloud'),
('AWS S3',             'aws s3',             'Cloud'),
('AWS EC2',            'aws ec2',            'Cloud'),
('AWS RDS',            'aws rds',            'Cloud'),
('AWS ECS',            'aws ecs',            'Cloud'),
('AWS EKS',            'aws eks',            'Cloud'),
('AWS CloudFormation', 'aws cloudformation', 'Cloud'),
('AWS CDK',            'aws cdk',            'Cloud'),
('AWS SQS',            'aws sqs',            'Cloud'),
('Azure Functions',    'azure functions',    'Cloud'),
('Azure DevOps',       'azure devops',       'Cloud'),
('Azure AKS',          'azure aks',          'Cloud'),
('Google Cloud Run',   'google cloud run',   'Cloud'),
('BigQuery',           'bigquery',           'Cloud'),
('Vertex AI',          'vertex ai',          'Cloud'),
('Vercel',             'vercel',             'Cloud'),
('Netlify',            'netlify',            'Cloud'),
('Heroku',             'heroku',             'Cloud'),
('DigitalOcean',       'digitalocean',       'Cloud'),
('Cloudflare',         'cloudflare',         'Cloud'),
('Hetzner',            'hetzner',            'Cloud'),
('Fly.io',             'fly.io',             'Cloud'),
('Railway',            'railway',            'Cloud'),

-- DevOps (additions)
('Ansible',        'ansible',        'DevOps'),
('Helm',           'helm',           'DevOps'),
('ArgoCD',         'argocd',         'DevOps'),
('FluxCD',         'fluxcd',         'DevOps'),
('Jenkins',        'jenkins',        'DevOps'),
('GitHub Actions', 'github actions', 'DevOps'),
('GitLab CI/CD',   'gitlab ci/cd',   'DevOps'),
('CircleCI',       'circleci',       'DevOps'),
('Prometheus',     'prometheus',     'DevOps'),
('Grafana',        'grafana',        'DevOps'),
('Loki',           'loki',           'DevOps'),
('Datadog',        'datadog',        'DevOps'),
('Sentry',         'sentry',         'DevOps'),
('New Relic',      'new relic',      'DevOps'),
('Nginx',          'nginx',          'DevOps'),
('Traefik',        'traefik',        'DevOps'),
('Istio',          'istio',          'DevOps'),
('Pulumi',         'pulumi',         'DevOps'),
('Vagrant',        'vagrant',        'DevOps'),
('Packer',         'packer',         'DevOps'),
('Vault',          'vault',          'DevOps'),
('Consul',         'consul',         'DevOps'),
('OpenTelemetry',  'opentelemetry',  'DevOps'),
('Jaeger',         'jaeger',         'DevOps'),
('Zipkin',         'zipkin',         'DevOps'),

-- Tools (additions)
('GitHub',         'github',         'Tool'),
('GitLab',         'gitlab',         'Tool'),
('Bitbucket',      'bitbucket',      'Tool'),
('Jira',           'jira',           'Tool'),
('Confluence',     'confluence',     'Tool'),
('Notion',         'notion',         'Tool'),
('Linear',         'linear',         'Tool'),
('Figma',          'figma',          'Tool'),
('Miro',           'miro',           'Tool'),
('Postman',        'postman',        'Tool'),
('Insomnia',       'insomnia',       'Tool'),
('VS Code',        'vs code',        'Tool'),
('IntelliJ IDEA',  'intellij idea',  'Tool'),
('PyCharm',        'pycharm',        'Tool'),
('WebStorm',       'webstorm',       'Tool'),
('Vim',            'vim',            'Tool'),
('Neovim',         'neovim',         'Tool'),
('Webpack',        'webpack',        'Tool'),
('Vite',           'vite',           'Tool'),
('ESLint',         'eslint',         'Tool'),
('Prettier',       'prettier',       'Tool'),
('Maven',          'maven',          'Tool'),
('Gradle',         'gradle',         'Tool'),
('npm',            'npm',            'Tool'),
('pnpm',           'pnpm',           'Tool'),
('Yarn',           'yarn',           'Tool'),
('SonarQube',      'sonarqube',      'Tool'),
('Swagger',        'swagger',        'Tool'),
('Makefile',       'makefile',       'Tool'),

-- API / Integration
('REST API',       'rest api',       'API'),
('OpenAPI / Swagger', 'openapi / swagger', 'API'),
('OAuth 2.0',      'oauth 2.0',      'API'),
('JWT',            'jwt',            'API'),
('SAML',           'saml',           'API'),
('WebSocket',      'websocket',      'API'),
('WebRTC',         'webrtc',         'API'),
('Apache Kafka',   'apache kafka',   'API'),
('RabbitMQ',       'rabbitmq',       'API'),
('MQTT',           'mqtt',           'API'),
('Apache Pulsar',  'apache pulsar',  'API'),
('NATS',           'nats',           'API'),
('SSE',            'sse',            'API'),

-- AI / ML (additions)
('Computer Vision',     'computer vision',     'AI/ML'),
('NLP',                 'nlp',                 'AI/ML'),
('Reinforcement Learning','reinforcement learning','AI/ML'),
('MLOps',               'mlops',               'AI/ML'),
('Data Science',        'data science',        'AI/ML'),
('Feature Engineering', 'feature engineering', 'AI/ML'),
('RAG',                 'rag',                 'AI/ML'),
('Prompt Engineering',  'prompt engineering',  'AI/ML'),
('Vector Embeddings',   'vector embeddings',   'AI/ML'),
('Model Fine-tuning',   'model fine-tuning',   'AI/ML'),
('Apache Spark',        'apache spark',        'AI/ML'),
('dbt',                 'dbt',                 'AI/ML'),
('Airflow',             'airflow',             'AI/ML'),
('OpenAI API',          'openai api',          'AI/ML'),
('Langchain',           'langchain',           'AI/ML'),
('Ollama',              'ollama',              'AI/ML'),

-- Architecture (additions)
('Domain-Driven Design',  'domain-driven design',  'Architecture'),
('Clean Architecture',    'clean architecture',    'Architecture'),
('Hexagonal Architecture','hexagonal architecture','Architecture'),
('CQRS',                  'cqrs',                  'Architecture'),
('Event Sourcing',        'event sourcing',        'Architecture'),
('Serverless',            'serverless',            'Architecture'),
('API Gateway',           'api gateway',           'Architecture'),
('Service Mesh',          'service mesh',          'Architecture'),
('Modular Monolith',      'modular monolith',      'Architecture'),
('Saga Pattern',          'saga pattern',          'Architecture'),
('SOLID',                 'solid',                 'Architecture'),
('Design Patterns',       'design patterns',       'Architecture'),

-- Testing
('Unit Testing',        'unit testing',        'Testing'),
('Integration Testing', 'integration testing', 'Testing'),
('End-to-End Testing',  'end-to-end testing',  'Testing'),
('BDD',                 'bdd',                 'Testing'),
('Load Testing',        'load testing',        'Testing'),
('Performance Testing', 'performance testing', 'Testing'),
('Contract Testing',    'contract testing',    'Testing'),
('Test Automation',     'test automation',     'Testing'),
('JUnit',               'junit',               'Testing'),
('Mockito',             'mockito',             'Testing'),
('pytest',              'pytest',              'Testing'),
('Testcontainers',      'testcontainers',      'Testing'),
('k6',                  'k6',                  'Testing'),
('Gatling',             'gatling',             'Testing'),
('Selenium',            'selenium',            'Testing'),

-- Security
('OWASP',                 'owasp',                 'Security'),
('Penetration Testing',   'penetration testing',   'Security'),
('GDPR',                  'gdpr',                  'Security'),
('Zero Trust',            'zero trust',            'Security'),
('SSO',                   'sso',                   'Security'),
('MFA / 2FA',             'mfa / 2fa',             'Security'),
('Cryptography',          'cryptography',          'Security'),
('TLS / SSL',             'tls / ssl',             'Security'),
('Vulnerability Assessment','vulnerability assessment','Security'),
('ISO 27001',             'iso 27001',             'Security'),
('SOC 2',                 'soc 2',                 'Security'),
('SIEM',                  'siem',                  'Security'),

-- Methodology (additions)
('Kanban',              'kanban',              'Methodology'),
('SAFe',                'safe',                'Methodology'),
('Extreme Programming', 'extreme programming', 'Methodology'),
('Lean',                'lean',                'Methodology'),
('Shape Up',            'shape up',            'Methodology'),
('Pair Programming',    'pair programming',    'Methodology'),
('Mob Programming',     'mob programming',     'Methodology'),
('Code Review',         'code review',         'Methodology'),
('OKRs',                'okrs',                'Methodology'),
('Design Thinking',     'design thinking',     'Methodology'),

-- Soft Skills
('Communication',        'communication',        'Soft Skill'),
('Leadership',           'leadership',           'Soft Skill'),
('Problem Solving',      'problem solving',      'Soft Skill'),
('Teamwork',             'teamwork',             'Soft Skill'),
('Adaptability',         'adaptability',         'Soft Skill'),
('Time Management',      'time management',      'Soft Skill'),
('Critical Thinking',    'critical thinking',    'Soft Skill'),
('Creativity',           'creativity',           'Soft Skill'),
('Attention to Detail',  'attention to detail',  'Soft Skill'),
('Conflict Resolution',  'conflict resolution',  'Soft Skill'),
('Mentoring',            'mentoring',            'Soft Skill'),
('Coaching',             'coaching',             'Soft Skill'),
('Presentation Skills',  'presentation skills',  'Soft Skill'),
('Stakeholder Management','stakeholder management','Soft Skill'),
('Negotiation',          'negotiation',          'Soft Skill'),
('Empathy',              'empathy',              'Soft Skill'),
('Self-motivation',      'self-motivation',      'Soft Skill'),
('Proactivity',          'proactivity',          'Soft Skill'),
('Written Communication','written communication','Soft Skill'),
('Cross-functional Collaboration','cross-functional collaboration','Soft Skill'),

-- Domain
('Backend Development',        'backend development',        'Domain'),
('Frontend Development',       'frontend development',       'Domain'),
('Full-Stack Development',     'full-stack development',     'Domain'),
('Mobile Development',         'mobile development',         'Domain'),
('Embedded Systems',           'embedded systems',           'Domain'),
('IoT',                        'iot',                        'Domain'),
('Game Development',           'game development',           'Domain'),
('Data Engineering',           'data engineering',           'Domain'),
('Platform Engineering',       'platform engineering',       'Domain'),
('Site Reliability Engineering','site reliability engineering','Domain'),
('FinTech',                    'fintech',                    'Domain'),
('HealthTech',                 'healthtech',                 'Domain'),
('EdTech',                     'edtech',                     'Domain'),
('E-commerce',                 'e-commerce',                 'Domain'),
('Blockchain',                 'blockchain',                 'Domain'),
('System Design',              'system design',              'Domain'),
('API Design',                 'api design',                 'Domain'),
('Database Design',            'database design',            'Domain'),
('Technical Writing',          'technical writing',          'Domain')

ON CONFLICT (name) DO NOTHING;
