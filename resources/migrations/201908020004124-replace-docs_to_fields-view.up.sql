DROP VIEW IF EXISTS vetd.docs_to_fields;
--;;
CREATE OR REPLACE VIEW vetd.docs_to_fields AS SELECT "d"."id" AS "doc_id", "d"."dtype" AS "doc_dtype", "d"."dsubtype" AS "doc_dsubtype", "d"."title" AS "doc_title", "d"."from_user_id" AS "doc_from_user_id", "d"."to_org_id" AS "doc_to_org_id", "d"."from_org_id" AS "doc_from_org_id", "d"."to_user_id" AS "doc_to_user_id", "d"."subject" AS "doc_subject", "d"."result" AS "doc_result", "d"."reason" AS "doc_reason", "r"."subject" AS "response_subject", "r"."subject_type" AS "response_subject_type", "rf"."id" AS "resp_field_id", "rf"."nval" AS "resp_field_nval", "rf"."sval" AS "resp_field_sval", "rf"."dval" AS "resp_field_dval", "rf"."jval" AS "resp_field_jval", "rf"."idx" AS "resp_field_idx", "p"."id" AS "prompt_id", "p"."prompt" AS "prompt_prompt", "p"."term" AS "prompt_term", "pf"."id" AS "prompt_field_id", "pf"."fname" AS "prompt_field_fname", "pf"."list_qm" AS "prompt_field_list_qm", "pf"."ftype" AS "prompt_field_ftype", "pf"."fsubtype" AS "prompt_field_fsubtype", "pf"."sort" AS "prompt_field_sort" FROM "docs" "d" INNER JOIN "doc_resp" "dr" ON ("d"."id" = "dr"."doc_id" AND "dr"."deleted" IS NULL) INNER JOIN "responses" "r" ON ("dr"."resp_id" = "r"."id" AND "r"."deleted" IS NULL) INNER JOIN "resp_fields" "rf" ON ("rf"."resp_id" = "r"."id" AND "rf"."deleted" IS NULL) INNER JOIN "prompts" "p" ON ("r"."prompt_id" = "p"."id" AND "p"."deleted" IS NULL) INNER JOIN "prompt_fields" "pf" ON ("pf"."id" = "rf"."pf_id" AND "pf"."deleted" IS NULL) WHERE "d"."deleted" IS NULL;
--;;
ALTER VIEW vetd.docs_to_fields OWNER TO vetd
--;;
GRANT SELECT ON vetd.docs_to_fields TO hasura;