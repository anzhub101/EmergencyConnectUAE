-- =====================================================================
-- EmergencyConnectUAE - Migration 03: Auth -> users sync
-- CSC408 | Assignment 3 | Spring 2026 | Abu Dhabi University
--
-- The public.users row id MUST equal the Supabase Auth user id (sub claim).
-- This trigger keeps the application profile table in sync automatically
-- whenever an account is created in Supabase Auth. The role is read from
-- app_metadata.role (set when you create the user / via the dashboard).
-- =====================================================================

CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    meta_role TEXT;
    mapped_role TEXT;
BEGIN
    meta_role := COALESCE(NEW.raw_app_meta_data ->> 'role', 'read_only');

    mapped_role := CASE lower(meta_role)
        WHEN 'dispatcher'      THEN 'ROLE_DISPATCHER'
        WHEN 'responder'       THEN 'ROLE_RESPONDER'
        WHEN 'hospital_admin'  THEN 'ROLE_HOSPITAL_ADMIN'
        WHEN 'hospital admin'  THEN 'ROLE_HOSPITAL_ADMIN'
        WHEN 'system_admin'    THEN 'ROLE_SYSTEM_ADMIN'
        WHEN 'system admin'    THEN 'ROLE_SYSTEM_ADMIN'
        WHEN 'read_only'       THEN 'ROLE_READ_ONLY'
        WHEN 'read only'       THEN 'ROLE_READ_ONLY'
        -- allow already-prefixed values too
        WHEN 'role_dispatcher'     THEN 'ROLE_DISPATCHER'
        WHEN 'role_responder'      THEN 'ROLE_RESPONDER'
        WHEN 'role_hospital_admin' THEN 'ROLE_HOSPITAL_ADMIN'
        WHEN 'role_system_admin'   THEN 'ROLE_SYSTEM_ADMIN'
        WHEN 'role_read_only'      THEN 'ROLE_READ_ONLY'
        ELSE 'ROLE_READ_ONLY'
    END;

    INSERT INTO public.users (id, email, role, is_active, created_at)
    VALUES (NEW.id, NEW.email, mapped_role, true, now())
    ON CONFLICT (id) DO UPDATE
        SET email = EXCLUDED.email,
            role  = EXCLUDED.role;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT OR UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();
