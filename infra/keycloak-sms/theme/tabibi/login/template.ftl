<#-- Mise en page commune à TOUTES les pages de connexion Keycloak (y compris erreur, page expirée, info) :
     aucune page n'utilise le style par défaut de Keycloak. -->
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<#assign lang = (locale.currentLanguageTag)!"fr">
<#assign rtl = lang == "ar">
<!DOCTYPE html>
<html lang="${lang}" dir="${rtl?then('rtl', 'ltr')}" translate="no">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <#-- Les textes existent déjà en français et en arabe : pas de traduction automatique (« نقل » deviendrait « Transfer »). -->
  <meta name="google" content="notranslate">
  <meta name="color-scheme" content="light">
  <title>${msg("app.name")}</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&family=Noto+Kufi+Arabic:wght@400;600;700&display=swap">
  <#list properties.styles?split(' ') as style><link rel="stylesheet" href="${url.resourcesPath}/${style}"></#list>
</head>
<body class="n-root">
  <div class="kc-page">
    <div class="kc-shell">
      <header class="kc-top">
        <span class="kc-logo">${msg("app.name")}<i>.</i></span>
        <#if locale?? && locale.supported?size gt 1>
          <#list locale.supported as l>
            <#if l.languageTag != lang><a class="kc-lang" href="${l.url}" hreflang="${l.languageTag}">${msg("common.language")}</a></#if>
          </#list>
        </#if>
      </header>
      <main class="kc-card">
        <#nested "header">
        <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
          <div class="n-banner n-banner--${(message.type == 'success')?then('success', (message.type == 'info')?then('info', 'error'))}" role="alert">
            <span>${kcSanitize(message.summary)?no_esc}</span>
          </div>
        </#if>
        <#nested "form">
        <#if displayInfo><#nested "info"></#if>
      </main>
    </div>
  </div>
</body>
</html>
</#macro>
