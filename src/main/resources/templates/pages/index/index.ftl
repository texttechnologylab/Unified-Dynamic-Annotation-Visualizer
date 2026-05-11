<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Unified Dynamic Annotation Visualizer | UDAV</title>
    <meta name="description" content="UDAV is designed to enable different disciplines to display their automatic pre-processing results in a schema-based and reproducible, dynamic and interactive way without the need to hard-code manual and user-defined visualizations for each new project.">

    <link rel="stylesheet" href="/css/variables.css" />
    <link rel="stylesheet" href="/css/pages/index.css" />
    <link rel="stylesheet" href="/css/shared/globals.css" />
    <link rel="stylesheet" href="/css/shared/components.css" />
    <link rel="stylesheet" href="/css/shared/controls.css" />
    <link rel="stylesheet" href="/packages/bootstrap-5.3.8/package/dist/css/bootstrap.min.css" />
    <link rel="stylesheet" href="/packages/bootstrap-icons-1.13.1/package/font/bootstrap-icons.min.css" />
  </head>
  
  <body>
    <#include "/shared/modal.ftl">
    <#include "/shared/fileInput.ftl">

    <#function safeFilename str>
      <#return str
        ?replace("[<>:\"/\\\\|?*\\x00-\\x1F]", "", "r")
        ?replace("\\s+", "-", "r")
      >
    </#function>

    <div class="dv-layout">
      <div class="dv-main-title">
        <div class="dv-main-title-logo">
          <img src="/img/logo.png" alt="Logo">
          <h2>UDAV</h2>
        </div>
        <h4>Unified Dynamic Annotation Visualizer</h4>
      </div>

      <div class="dv-menu-container">
        <div class="dv-title">Select a pipeline</div>
        <div class="dv-menu">
          <div class="dv-menu-item-list">
            <#list pipelines?eval_json as pipeline>
              <div id="pipeline-${pipeline.id}" class="dv-btn dv-menu-item">
                <a
                  class="dv-menu-link"
                  title="Select pipeline"
                  href="/view/${pipeline.id}"
                >
                  <i class="bi bi-clipboard-data"></i>
                  <span>${pipeline.name}</span>
                </a>

                <#if !lockedPipelines?seq_contains(pipeline.id)>
                  <a
                    class="dv-btn-hidden"
                    title="Edit configuration"
                    href="/editor/${pipeline.id}"
                  >
                    <i class="bi bi-pencil"></i>
                  </a>
                </#if>
                <a
                  class="dv-btn-hidden"
                  title="Export configuration"
                  href="/api/pipelines/${pipeline.id}?pretty=true"
                  download="${safeFilename(pipeline.name)}.json"
                >
                  <i class="bi bi-download"></i>
                </a>
                <button
                  class="dv-btn-hidden dv-btn-delete"
                  title="Delete pipeline"
                  data-dv-toggle="modal"
                  data-id="${pipeline.id}"
                  data-name="${pipeline.name}"
                >
                  <i class="bi bi-trash"></i>
                </button>
              </div>
            </#list>
          </div>

          <#if pipelines?eval_json?size != 0>
            <div class="dv-divider"></div>
          </#if>

          <div class="dv-menu-item">
            <a
              class="dv-btn dv-menu-link"
              title="Create pipeline"
              href="/editor"
            >
              <i class="bi bi-plus-lg"></i>
              <span>Create new pipeline</span>
            </a>
          </div>
        </div>

        <div class="dv-separator">OR</div>

        <div class="dv-title">Start with a json configuration</div>
        <@fileInput info="Single file • JSON" accept="application/json" />
      </div>

      <@modal />
    </div>

    <script type="module">
      import Menu from "/js/pages/index/Menu.js";
      
      const menu = new Menu();
      menu.init();
    </script>
  </body>
</html>
