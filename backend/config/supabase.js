import {createClient} from '@supabase/supabase-js'

const supabaseURL = process.env.SUPABASE_URL;
const supabaseKEY = process.env.SUPABASE_KEY; 

const supabase = createClient(supabaseURL, supabaseKEY); 

export default supabase; 