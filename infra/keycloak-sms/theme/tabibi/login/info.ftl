<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <div class="n-state n-state--success">
      <div class="n-state__icon" aria-hidden="true">✓</div>
      <#if message?has_content><p class="n-body">${kcSanitize(message.summary)?no_esc}</p></#if>
    </div>
  <#elseif section = "form">
    <#if pageRedirectUri?has_content>
      <a class="n-btn n-btn--primary n-btn--lg n-btn--block" href="${pageRedirectUri}">${msg("common.continue")}</a>
    <#elseif actionUri?has_content>
      <a class="n-btn n-btn--primary n-btn--lg n-btn--block" href="${actionUri}">${msg("common.continue")}</a>
    <#elseif (client.baseUrl)?has_content>
      <a class="n-btn n-btn--secondary n-btn--block" href="${client.baseUrl}">${msg("common.back")}</a>
    </#if>
  </#if>
</@layout.registrationLayout>
