DO $$ 
DECLARE 
    rec RECORD;
    max_seqno INTEGER;
    curr_tree_id VARCHAR(32);
    root_treenode_id VARCHAR(32);
    curr_cli_is_system BOOLEAN;
BEGIN
    FOR cli in (select * from ad_client)
    loop
        if cli.ad_client_id = '0' then
            curr_cli_is_system := true;
        else
            curr_cli_is_system := false;
        end if;
        -- check if the tree exists for table
        SELECT ad_tree_id
        INTO curr_tree_id
        FROM ad_tree
        WHERE 
        ad_client_id = cli.ad_client_id
        and ad_table_id = '6344EB0DE29E4E52ACF99F591FFCD07D';
        --if not exists create tree
        IF curr_tree_id IS NULL THEN
            RAISE NOTICE 'Creating Copilot App tree for client %', cli.ad_client_id;
            curr_tree_id := get_uuid();
            INSERT INTO ad_tree
            (ad_tree_id, ad_client_id, ad_org_id, created, createdby, updated, updatedby, isactive, "name", description, treetype, isallnodes, ad_table_id)
            VALUES(curr_tree_id,  cli.ad_client_id, '0', now(), '100', now(), '100', 'Y', 'ETCOP_App', NULL, 'ETCOP_App', 'Y', '6344EB0DE29E4E52ACF99F591FFCD07D');
        END IF;
        -- every tree has a root node
        SELECT ad_treenode_id
        INTO root_treenode_id
        FROM ad_treenode
        WHERE ad_tree_id = curr_tree_id
        and parent_id is null;

        --if not exists create root node
        IF curr_root_treenode_id IS NULL THEN
            RAISE NOTICE 'Creating root node for tree %', curr_tree_id;
            curr_root_treenode_id := get_uuid();
            INSERT INTO ad_treenode
            (ad_treenode_id, ad_tree_id, node_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, parent_id, seqno)
            VALUES(curr_root_treenode_id, curr_tree_id, '0', cli.ad_client_id, '0', 'Y', now(), '100', now(), '100', null, 0);
        END IF;

        -- Obtener el seqno máximo para el ad_tree_id específico una sola vez

        SELECT MAX(seqno)
        INTO max_seqno
        FROM ad_treenode at2
        WHERE at2.ad_tree_id = curr_tree_id;

        -- traigo las apps que deberian estar en el arbol, pero que no estan
        -- si el cliente actual es sistem, solo deberia ener en cuenta las apps que tienen client_id = 0
        FOR rec IN 
            SELECT * FROM etcop_app app
            WHERE app.ad_client_id = cli.ad_client_id or 
            (app.ad_client_id != cli.ad_client_id and curr_cli_is_system = true and 


            )
            WHERE NOT EXISTS (
                SELECT 1
                FROM ad_treenode at2
                WHERE at2.ad_tree_id = curr_tree_id
                AND at2.node_id = app.etcop_app_id
            )
        LOOP
            INSERT INTO ad_treenode (
              ad_treenode_id,
              ad_tree_id,
      
                  node_id,
              ad_client_id,
              ad_org_id,
      
                  isactive,
              created,
              createdby,
      
                  updated,
              updatedby,
              parent_id,
      
                  seqno
          )
      VALUES(
              get_uuid(),
'D9D1766B86694B849978AF1C99C06DBB',
              rec.etcop_app_id,
      
                 rec.ad_client_id,
              '0',
              'Y',
      
                  now(),
              '100',
              now(),
              '100',
      
                  '0',
              max_seqno + 10
          );
      
          max_seqno := max_seqno + 10;
      end loop;
      END $$

            -- Incrementar el max_seqno para la próxima iteración si es necesario
            max_seqno := max_seqno + 1;
        END LOOP;
    end loop;
END $$;