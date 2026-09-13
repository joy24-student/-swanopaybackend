import { createClient } from '@supabase/supabase-js'
import { requireMerchantOrAdminAuth } from './auth.js'
import { getMerchantCredentials } from '../services/adminSupabase.js'

// First accept platform authentication. Otherwise verify against the merchant's
// registered project, then check ownership with that user's JWT and RLS.
export async function requireShopAuth(req,res,next) {
  const fallback = async () => {
    try {
      const token = (req.headers.authorization || '').replace(/^Bearer\s+/i,'').trim()
      if (token.split('.').length !== 3) return res.status(401).json({ok:false,error:'Sign in with your Supabase merchant account.'})
      const credentials = await getMerchantCredentials(req.shopMerchantId)
      if (credentials?.supabase_url && credentials.supabase_anon_key) {
        const url = new URL(credentials.supabase_url)
        const allowed=(process.env.SHOP_AUTH_HOSTS || '').split(',').map(x=>x.trim()).filter(Boolean)
        if(url.protocol !== 'https:' || !(url.hostname.endsWith('.supabase.co') || allowed.includes(url.hostname))) throw new Error('Unapproved auth host')
        const client=createClient(url.href,credentials.supabase_anon_key,{auth:{persistSession:false,autoRefreshToken:false},global:{headers:{Authorization:`Bearer ${token}`}}})
        const {data,error}=await client.auth.getUser(token)
        if(!error && data.user?.id) {
          const {data:merchant,error:ownerError}=await client.from('merchants').select('id,user_id').eq('id',req.shopMerchantId).eq('user_id',data.user.id).maybeSingle()
          if(!ownerError && merchant) { req.merchantUser=data.user; return next() }
        }
      }
    } catch { /* A failed verification never grants access. */ }
    return res.status(401).json({ok:false,error:'Sign in as the owner of this registered merchant to manage its website.'})
  }
  // The existing middleware writes only an unauthorized response on failure.
  // Defer that response while trying the registered merchant project.
  const authResponse={status(){return this},json(){return fallback()}}
  return requireMerchantOrAdminAuth(req,authResponse,next)
}
