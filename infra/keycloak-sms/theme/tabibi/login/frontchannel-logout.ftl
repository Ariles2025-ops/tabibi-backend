<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <div class="kc-heading"><h1>${msg("auth.logout.done")}</h1></div>
    <#list logout.clients as c><iframe src="${c.frontChannelLogoutUrl}" hidden title="${c.name}"></iframe></#list>
  <#elseif section = "form">
    <#if logout.logoutRedirectUri?has_content>
      <a class="n-btn n-btn--primary n-btn--lg n-btn--block" href="${logout.logoutRedirectUri}">${msg("common.continue")}</a>
    </#if>
  </#if>
</@layout.registrationLayout>
