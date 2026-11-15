<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <div class="n-state n-state--error">
      <div class="n-state__icon" aria-hidden="true">!</div>
      <h1 class="n-title-md">${msg("auth.page.error")}</h1>
      <#if message?has_content><p class="n-small n-secondary">${kcSanitize(message.summary)?no_esc}</p></#if>
    </div>
  <#elseif section = "form">
    <#if client?? && client.baseUrl?has_content>
      <a class="n-btn n-btn--secondary n-btn--block" href="${client.baseUrl}">${msg("common.back")}</a>
    </#if>
  </#if>
</@layout.registrationLayout>
