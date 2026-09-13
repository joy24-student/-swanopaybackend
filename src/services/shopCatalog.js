import fs from 'node:fs/promises'
import path from 'node:path'
import crypto from 'node:crypto'
import sharp from 'sharp'
import { ShopError } from './shopValidation.js'

const invalid = message => { throw new ShopError(400,'INVALID_PRODUCT_DETAILS',message) }
const field = (value, max=20000) => {
  if (value !== undefined && typeof value !== 'string') invalid('Product text fields must contain text.')
  const text=(value || '').trim()
  if(text.length>max || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(text)) invalid(`Keep product text within ${max} characters.`)
  return text
}
const option = value => {
  const name=field(value,100)
  if(/[<>]/.test(name)) invalid('Use plain text for categories, sizes and colors.')
  return name
}
const imageName = value => {
  if(!value) return ''
  if(typeof value!=='string' || !/^product-[a-f0-9]{64}\.jpg$/.test(value)) invalid('Upload product images before publishing them.')
  return value
}
export function productDetails(value) {
  if(value===undefined) return null // Older clients must preserve website-only fields.
  if(!value || Array.isArray(value) || typeof value!=='object') invalid('Invalid storefront product details.')
  const oldPrice=Number(value.old_price ?? 0)
  if(!Number.isFinite(oldPrice) || oldPrice<0 || oldPrice>99999999) invalid('Enter a valid old price.')
  const list=(values,max,transform)=>{
    if(!Array.isArray(values) || values.length>max) invalid(`Choose up to ${max} product options or images.`)
    return [...new Set(values.map(transform).filter(Boolean))]
  }
  const video=field(value.video_url,255)
  if(video) {
    let url;try {url=new URL(video)}catch {invalid('Enter a valid YouTube video URL.')}
    if(url.protocol!=='https:' || !['youtube.com','www.youtube.com','youtu.be'].includes(url.hostname)) invalid('Use an HTTPS YouTube video URL.')
  }
  return {
    old_price:oldPrice,top_category:option(value.top_category) || 'Shop',mid_category:option(value.mid_category) || 'All products',end_category:option(value.end_category) || 'General',
    description:field(value.description),short_description:field(value.short_description),features:field(value.features),condition:field(value.condition),return_policy:field(value.return_policy),video_url:video,
    sizes:list(value.sizes ?? [],50,option),colors:list(value.colors ?? [],50,option),featured_image:imageName(value.featured_image),gallery:list(value.gallery ?? [],10,imageName),
    is_featured:value.is_featured===true,is_active:value.is_active!==false
  }
}
const html = text => text.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;').replaceAll('\n','<br>')

export async function saveCatalogItems(client, records, uploadRoot) {
  // Serialise category/option creation with PHP admin writes in this tenant only.
  await client.query('LOCK TABLE tbl_top_category,tbl_mid_category,tbl_end_category,tbl_size,tbl_color IN SHARE ROW EXCLUSIVE MODE')
  const named=async(table,key,column,name,parentColumn=null,parent=null)=>{
    const params=parentColumn ? [name,parent] : [name]
    const found=await client.query(`SELECT ${key} AS id FROM ${table} WHERE lower(${column})=lower($1)${parentColumn ? ` AND ${parentColumn}=$2` : ''} ORDER BY ${key} LIMIT 1`,params)
    if(found.rows.length) return found.rows[0].id
    const extra=table==='tbl_top_category' ? ",show_on_menu,tcat_order,photo" : ''
    const defaults=table==='tbl_top_category' ? ",1,1,'placeholder.svg'" : ''
    return (await client.query(`INSERT INTO ${table}(${column}${parentColumn ? ','+parentColumn : ''}${extra}) VALUES($1${parentColumn ? ',$2' : ''}${defaults}) RETURNING ${key} AS id`,params)).rows[0].id
  }
  for(const item of records) {
    const d=item.details
    let category=1
    if(d) {
      for(const [folder,filename] of [[uploadRoot,d.featured_image],...d.gallery.map(name=>[path.join(uploadRoot,'product_photos'),name])]) {
        if(filename) try {await fs.access(path.join(folder,filename))} catch {invalid('A product image is missing. Upload it again before publishing.')}
      }
      const top=await named('tbl_top_category','tcat_id','tcat_name',d.top_category)
      const mid=await named('tbl_mid_category','mcat_id','mcat_name',d.mid_category,'tcat_id',top)
      category=await named('tbl_end_category','ecat_id','ecat_name',d.end_category,'mcat_id',mid)
    }
    const result=await client.query(`INSERT INTO tbl_product(source_id,p_name,p_current_price,p_qty,p_description,p_is_featured,ecat_id,p_is_active)
      VALUES($1,$2,$3,$4,$5,$6,$7,1) ON CONFLICT(source_id) DO UPDATE SET p_name=$2,p_current_price=$3,p_qty=$4
      RETURNING p_id`,[item.sourceId,item.name,item.price,item.stock,html(item.description),item.featured,category])
    const id=result.rows[0].p_id
    if(!d) continue
    await client.query(`UPDATE tbl_product SET p_old_price=$2,p_description=$3,p_short_description=$4,p_feature=$5,p_condition=$6,p_return_policy=$7,p_video_link=$8,p_is_featured=$9,p_is_active=$10,ecat_id=$11,p_featured_photo=$12 WHERE p_id=$1`,
      [id,d.old_price,html(d.description),html(d.short_description),html(d.features),html(d.condition),html(d.return_policy),d.video_url,d.is_featured ? 1:0,d.is_active ? 1:0,category,d.featured_image || 'placeholder.svg'])
    for(const [values,table,key,column,join] of [[d.sizes,'tbl_size','size_id','size_name','tbl_product_size'],[d.colors,'tbl_color','color_id','color_name','tbl_product_color']]) {
      await client.query(`DELETE FROM ${join} WHERE p_id=$1`,[id])
      for(const name of values) {
        const optionId=await named(table,key,column,name)
        await client.query(`INSERT INTO ${join}(p_id,${key}) VALUES($1,$2)`,[id,optionId])
      }
    }
    await client.query('DELETE FROM tbl_product_photo WHERE p_id=$1',[id])
    for(const photo of d.gallery) await client.query('INSERT INTO tbl_product_photo(p_id,photo) VALUES($1,$2)',[id,photo])
  }
}

export async function storeProductImage(uploadRoot, body) {
  if(!['featured','gallery'].includes(body.kind) || typeof body.base64!=='string' || body.base64.length>1400000 || !/^[A-Za-z0-9+/]+={0,2}$/.test(body.base64)) throw new ShopError(400,'INVALID_IMAGE','Upload a JPG, PNG or WebP image up to 1 MB.')
  const input=Buffer.from(body.base64,'base64')
  if(input.length>1048576) throw new ShopError(413,'IMAGE_TOO_LARGE','Compress the image to 1 MB or less before upload.')
  let data
  try {
    const decoder=sharp(input,{limitInputPixels:40000000,failOn:'warning'})
    const meta=await decoder.metadata()
    if(!['jpeg','png','webp'].includes(meta.format) || (meta.pages || 1)>1) throw new Error('Unsupported image')
    data=await decoder.rotate().resize({width:2048,height:2048,fit:'inside',withoutEnlargement:true}).flatten({background:'#ffffff'}).jpeg({quality:82,mozjpeg:true}).toBuffer()
  } catch {throw new ShopError(400,'INVALID_IMAGE','The image could not be decoded. Choose a JPG, PNG or WebP photo.')}
  const filename=`product-${crypto.createHash('sha256').update(data).digest('hex')}.jpg`
  const folder=body.kind==='gallery' ? path.join(uploadRoot,'product_photos') : uploadRoot
  await fs.mkdir(folder,{recursive:true,mode:0o770})
  await fs.writeFile(path.join(folder,filename),data,{flag:'wx',mode:0o640}).catch(error=>{if(error.code!=='EEXIST') throw error})
  return {ok:true,filename,relative_path:`${body.kind==='gallery' ? 'product_photos/' : ''}${filename}`,bytes:data.length}
}
