(() => {
  const token=document.querySelector('meta[name="csrf-token"]')?.content;
  if(window.jQuery && token) window.jQuery.ajaxPrefilter((options,_original,xhr)=>{
    const url=new URL(options.url || location.href,location.href);
    if(url.origin===location.origin && !['GET','HEAD','OPTIONS'].includes((options.type || 'GET').toUpperCase())) xhr.setRequestHeader('X-CSRF-Token',token);
  });
})();
