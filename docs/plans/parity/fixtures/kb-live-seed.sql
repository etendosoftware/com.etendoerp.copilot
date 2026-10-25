INSERT INTO etcop_api_key (etcop_api_key_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, provider_type, api_key, base_url, ad_module_id, etcop_import_legacy)
SELECT 'AAAA0000000000000000000000000001','0','0','Y',now(),'100',now(),'100','KB smoke mock','etendo', api_key,'http://127.0.0.1:18765/v1', ad_module_id,'N'
FROM etcop_api_key WHERE name='Imported legacy openai';
UPDATE etcop_openai_model SET etcop_api_key_id='AAAA0000000000000000000000000001' WHERE searchkey='etendo-embeddings';
INSERT INTO etcop_app (etcop_app_id, name, ad_client_id,ad_org_id,isactive,created,createdby,updated,updatedby,description,openai_id_assistant,prompt,apptype,code_interpreter,retrieval,provider,openai_id_vectordb,ad_module_id,sync_assistant,refresh_graph_img,temperature,etcop_openai_model_id,check_hosts,search_k,mcp_config,structured_output)
SELECT 'AAAA0000000000000000000000000010','KB Smoke Agent', ad_client_id,ad_org_id,isactive,created,createdby,updated,updatedby,description,openai_id_assistant,prompt,apptype,code_interpreter,retrieval,provider,openai_id_vectordb,ad_module_id,sync_assistant,refresh_graph_img,temperature,etcop_openai_model_id,check_hosts,search_k,mcp_config,structured_output FROM etcop_app WHERE name='Task Evaluator';
INSERT INTO etcop_file (etcop_file_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, type, istemp, url, ad_module_id)
SELECT 'AAAA0000000000000000000000000021','0','0','Y',now(),'100',now(),'100','Smoke Doc A','RF','N','http://127.0.0.1:18766/docA.md', ad_module_id FROM etcop_file WHERE name='Etendo Wiki';
INSERT INTO etcop_file (etcop_file_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, name, type, istemp, url, ad_module_id)
SELECT 'AAAA0000000000000000000000000022','0','0','Y',now(),'100',now(),'100','Smoke Doc B','RF','N','http://127.0.0.1:18766/docB.md', ad_module_id FROM etcop_file WHERE name='Etendo Wiki';
INSERT INTO etcop_app_source (etcop_app_source_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, etcop_app_id, etcop_file_id, behaviour, excl_code_int, excl_retrieval, ad_module_id)
SELECT 'AAAA0000000000000000000000000031','0','0','Y',now(),'100',now(),'100','AAAA0000000000000000000000000010','AAAA0000000000000000000000000021','kb','N','N', ad_module_id FROM etcop_app_source WHERE behaviour='kb' LIMIT 1;
INSERT INTO etcop_app_source (etcop_app_source_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, etcop_app_id, etcop_file_id, behaviour, excl_code_int, excl_retrieval, ad_module_id)
SELECT 'AAAA0000000000000000000000000032','0','0','Y',now(),'100',now(),'100','AAAA0000000000000000000000000010','AAAA0000000000000000000000000022','kb','N','N', ad_module_id FROM etcop_app_source WHERE behaviour='kb' LIMIT 1;
INSERT INTO etcop_role_app VALUES (replace(gen_random_uuid()::text,'-',''),'0','0','Y',now(),'100',now(),'100','42D0EEB1C66F497A90DD526DC597E6F0','AAAA0000000000000000000000000010');
INSERT INTO etcop_role_app VALUES (replace(gen_random_uuid()::text,'-',''),'0','0','Y',now(),'100',now(),'100','42D0EEB1C66F497A90DD526DC597E6F0','4A610042B83042DC8014A713292E78B4');
UPDATE etcop_app SET sync_startup='N' WHERE name<>'KB Smoke Agent';
UPDATE etcop_app SET sync_startup='Y' WHERE name='KB Smoke Agent';
