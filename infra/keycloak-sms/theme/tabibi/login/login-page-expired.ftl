<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <div class="kc-heading"><h1>${msg("auth.page.expired")}</h1></div>
  <#elseif section = "form">
    <a class="n-btn n-btn--primary n-btn--lg n-btn--block" href="${url.loginRestartFlowUrl}">${msg("auth.page.restart")}</a>
  </#if>
</@layout.registrationLayout>
