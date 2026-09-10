-- Curated aliases: the abbreviations and alternate spellings a skill also answers to.
--
-- These are folded onto a row's canonical name at match time by SkillCanonicalizer, so a profile
-- that says "Kubernetes" is recognised by a posting that asks for "k8s", and vice versa. This is
-- deliberately NOT stemming — "k8s" is not an inflection of "kubernetes", it is a synonym, and only
-- a curated list catches it without the over-reporting a stemmer would cause.
--
-- Rules for adding entries here:
--   * An alias must be unambiguous. It resolves to exactly one skill; if a token could mean two
--     things (e.g. "tf" = Terraform vs TensorFlow), pick one and leave the other spelled out.
--   * An alias must not equal another taxonomy row's name — a distinct skill is never an alias.
--   * Keep them lowercase; matching is case-insensitive.

UPDATE skill_taxonomy SET aliases = '{k8s,kube}'                     WHERE normalized_name = 'kubernetes';
UPDATE skill_taxonomy SET aliases = '{postgres,postgre,psql,pg}'     WHERE normalized_name = 'postgresql';
UPDATE skill_taxonomy SET aliases = '{js}'                           WHERE normalized_name = 'javascript';
UPDATE skill_taxonomy SET aliases = '{ts}'                           WHERE normalized_name = 'typescript';
UPDATE skill_taxonomy SET aliases = '{gha}'                          WHERE normalized_name = 'github actions';
UPDATE skill_taxonomy SET aliases = '{tf}'                           WHERE normalized_name = 'terraform';
UPDATE skill_taxonomy SET aliases = '{node,nodejs}'                  WHERE normalized_name = 'node.js';
UPDATE skill_taxonomy SET aliases = '{next,nextjs}'                  WHERE normalized_name = 'next.js';
UPDATE skill_taxonomy SET aliases = '{nuxt,nuxtjs}'                  WHERE normalized_name = 'nuxt.js';
UPDATE skill_taxonomy SET aliases = '{vue,vuejs}'                    WHERE normalized_name = 'vue.js';
UPDATE skill_taxonomy SET aliases = '{dotnet,.net core}'            WHERE normalized_name = '.net';
UPDATE skill_taxonomy SET aliases = '{csharp,c sharp}'              WHERE normalized_name = 'c#';
UPDATE skill_taxonomy SET aliases = '{cpp,cplusplus}'              WHERE normalized_name = 'c++';
UPDATE skill_taxonomy SET aliases = '{golang}'                      WHERE normalized_name = 'go';
UPDATE skill_taxonomy SET aliases = '{amazon web services}'        WHERE normalized_name = 'aws';
UPDATE skill_taxonomy SET aliases = '{google cloud platform,google cloud}' WHERE normalized_name = 'gcp';
UPDATE skill_taxonomy SET aliases = '{microsoft azure}'            WHERE normalized_name = 'azure';
UPDATE skill_taxonomy SET aliases = '{sklearn}'                    WHERE normalized_name = 'scikit-learn';
UPDATE skill_taxonomy SET aliases = '{mongo}'                      WHERE normalized_name = 'mongodb';
UPDATE skill_taxonomy SET aliases = '{elastic}'                    WHERE normalized_name = 'elasticsearch';
UPDATE skill_taxonomy SET aliases = '{rails,ror}'                  WHERE normalized_name = 'ruby on rails';
UPDATE skill_taxonomy SET aliases = '{objc,obj-c}'                WHERE normalized_name = 'objective-c';
UPDATE skill_taxonomy SET aliases = '{tailwind}'                   WHERE normalized_name = 'tailwind css';
