<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <div class="kc-heading"><h1>${msg("auth.logout.title")}</h1></div>
  <#elseif section = "form">
    <form class="kc-form" action="${url.logoutConfirmAction}" method="post">
      <input type="hidden" name="session_code" value="${logoutConfirm.code}">
      <button class="n-btn n-btn--primary n-btn--lg n-btn--block" type="submit" name="confirmLogout">${msg("auth.logout.submit")}</button>
      <#if logoutConfirm.skipLink?? && !logoutConfirm.skipLink && (client.baseUrl)?has_content>
        <a class="n-btn n-btn--ghost n-btn--block" href="${client.baseUrl}">${msg("common.cancel")}</a>
      </#if>
    </form>
  </#if>
</@layout.registrationLayout>
