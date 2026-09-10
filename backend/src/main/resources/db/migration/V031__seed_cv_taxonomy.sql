-- Extend the taxonomy with skills and technologies present on real CVs but missing from the
-- V021 seed. Same rules as V021: name is the display form, normalized_name is lowercased/trimmed,
-- category uses the fixed vocabulary (Programming Language, Framework, Library, Database, Cloud,
-- DevOps, Tool, API, AI/ML, Architecture, Testing, Security, Methodology, Domain, Soft Skill).
-- ON CONFLICT (name) DO NOTHING so re-running (or overlap with a future seed) is harmless.
--
-- Deliberately excluded as too generic to match usefully: Object-Oriented Programming, Data
-- Structures & Algorithms, Data Modeling (Database Design covers it), Swift Concurrency (Swift).

INSERT INTO skill_taxonomy (name, normalized_name, category, aliases) VALUES
  -- Programming languages
  ('T-SQL', 't-sql', 'Programming Language', '{}'),

  -- Frameworks
  ('ASP.NET', 'asp.net', 'Framework', '{}'),
  ('ASP.NET Core', 'asp.net core', 'Framework', '{}'),
  ('ASP.NET MVC', 'asp.net mvc', 'Framework', '{}'),
  ('ASP.NET Web API', 'asp.net web api', 'Framework', '{}'),
  ('Blazor', 'blazor', 'Framework', '{}'),
  ('WPF', 'wpf', 'Framework', '{}'),
  ('JavaFX', 'javafx', 'Framework', '{}'),
  ('Spring Data JPA', 'spring data jpa', 'Framework', '{}'),
  ('Spring Security', 'spring security', 'Framework', '{}'),
  ('Symfony', 'symfony', 'Framework', '{}'),
  ('Shopware', 'shopware', 'Framework', '{shopware 6}'),
  ('Pug', 'pug', 'Framework', '{}'),
  ('Twig', 'twig', 'Framework', '{}'),
  ('Material UI', 'material ui', 'Framework', '{mui}'),
  ('SwiftUI', 'swiftui', 'Framework', '{}'),
  ('Sass', 'sass', 'Framework', '{scss}'),
  ('iOS', 'ios', 'Framework', '{}'),
  ('Android', 'android', 'Framework', '{}'),
  ('LG webOS', 'lg webos', 'Framework', '{webos}'),

  -- Libraries
  ('Entity Framework', 'entity framework', 'Library', '{ef,ef6,ef core,entity framework core}'),
  ('ADO.NET', 'ado.net', 'Library', '{}'),
  ('Symfony Messenger', 'symfony messenger', 'Library', '{}'),
  ('MapKit', 'mapkit', 'Library', '{}'),
  ('Core Location', 'core location', 'Library', '{}'),
  ('Autofac', 'autofac', 'Library', '{}'),

  -- Databases
  ('H2', 'h2', 'Database', '{}'),
  ('Typesense', 'typesense', 'Database', '{}'),

  -- Cloud
  ('Azure SQL', 'azure sql', 'Cloud', '{}'),

  -- DevOps
  ('Docker Compose', 'docker compose', 'DevOps', '{}'),
  ('DDEV', 'ddev', 'DevOps', '{}'),

  -- Tools
  ('Flyway', 'flyway', 'Tool', '{}'),
  ('mise', 'mise', 'Tool', '{}'),
  ('phpMyAdmin', 'phpmyadmin', 'Tool', '{}'),
  ('Xdebug', 'xdebug', 'Tool', '{}'),
  ('Swift Package Manager', 'swift package manager', 'Tool', '{spm}'),
  ('n8n', 'n8n', 'Tool', '{}'),
  ('Visual Studio', 'visual studio', 'Tool', '{}'),
  ('JetBrains Rider', 'jetbrains rider', 'Tool', '{rider}'),
  ('Xcode', 'xcode', 'Tool', '{}'),
  ('Android Studio', 'android studio', 'Tool', '{}'),
  ('PhpStorm', 'phpstorm', 'Tool', '{}'),
  ('Trello', 'trello', 'Tool', '{}'),
  ('Wireshark', 'wireshark', 'Tool', '{}'),
  ('Visual Paradigm', 'visual paradigm', 'Tool', '{}'),
  ('Balsamiq', 'balsamiq', 'Tool', '{}'),

  -- APIs & integration
  ('GitHub API', 'github api', 'API', '{}'),
  ('Webhooks', 'webhooks', 'API', '{webhook}'),

  -- AI / ML
  ('Google Gemini', 'google gemini', 'AI/ML', '{gemini}'),
  ('Claude', 'claude', 'AI/ML', '{anthropic claude}'),
  ('Model Context Protocol', 'model context protocol', 'AI/ML', '{mcp}'),

  -- Testing
  ('PHPUnit', 'phpunit', 'Testing', '{}'),
  ('Acceptance Testing', 'acceptance testing', 'Testing', '{}'),
  ('System Testing', 'system testing', 'Testing', '{}'),

  -- Architecture, patterns & systems concepts
  ('UML', 'uml', 'Architecture', '{unified modeling language}'),
  ('GRASP', 'grasp', 'Architecture', '{}'),
  ('MVC', 'mvc', 'Architecture', '{}'),
  ('MVVM', 'mvvm', 'Architecture', '{}'),
  ('Three-Tier Architecture', 'three-tier architecture', 'Architecture', '{3-tier architecture,n-tier architecture}'),
  ('Service-Oriented Architecture', 'service-oriented architecture', 'Architecture', '{soa}'),
  ('Dependency Injection', 'dependency injection', 'Architecture', '{ioc,inversion of control}'),
  ('Protocol-Oriented Programming', 'protocol-oriented programming', 'Architecture', '{}'),
  ('Distributed Systems', 'distributed systems', 'Architecture', '{}'),
  ('Multithreading', 'multithreading', 'Architecture', '{}'),
  ('Concurrency', 'concurrency', 'Architecture', '{}'),
  ('Socket Programming', 'socket programming', 'Architecture', '{sockets}'),

  -- Methodologies
  ('Unified Process', 'unified process', 'Methodology', '{}'),
  ('Waterfall', 'waterfall', 'Methodology', '{waterfall model}'),

  -- Soft skills (the rest of the CV's personal competencies already map to V021 rows)
  ('Analytical Thinking', 'analytical thinking', 'Soft Skill', '{analytical}'),
  ('Curiosity', 'curiosity', 'Soft Skill', '{curious}'),
  ('Reliability', 'reliability', 'Soft Skill', '{reliable,dependable}'),
  ('Business Acumen', 'business acumen', 'Soft Skill', '{business understanding}')
ON CONFLICT (name) DO NOTHING;

-- Aliases folding CV variant spellings onto existing V021 rows (matched at compare time by
-- SkillCanonicalizer). The .NET row already carries {dotnet,.net core} from V030, so its list is
-- restated in full with .net framework appended.
UPDATE skill_taxonomy SET aliases = '{express.js,expressjs}'              WHERE normalized_name = 'express';
UPDATE skill_taxonomy SET aliases = '{react.js,reactjs}'                  WHERE normalized_name = 'react';
UPDATE skill_taxonomy SET aliases = '{angularjs,angular.js}'              WHERE normalized_name = 'angular';
UPDATE skill_taxonomy SET aliases = '{html5}'                            WHERE normalized_name = 'html';
UPDATE skill_taxonomy SET aliases = '{css3}'                             WHERE normalized_name = 'css';
UPDATE skill_taxonomy SET aliases = '{dotnet,.net core,.net framework}' WHERE normalized_name = '.net';
UPDATE skill_taxonomy SET aliases = '{oauth2,oauth}'                     WHERE normalized_name = 'oauth 2.0';
UPDATE skill_taxonomy SET aliases = '{mssql,ms sql server,sql server}'  WHERE normalized_name = 'microsoft sql server';
UPDATE skill_taxonomy SET aliases = '{firebase auth,firebase authentication,firestore}' WHERE normalized_name = 'firebase';
UPDATE skill_taxonomy SET aliases = '{gitlab pipelines,gitlab ci}'      WHERE normalized_name = 'gitlab ci/cd';
