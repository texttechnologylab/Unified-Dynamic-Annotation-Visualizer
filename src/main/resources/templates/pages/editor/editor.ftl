<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Editor - Dynamic Visualizations</title>

    <link rel="stylesheet" href="/css/variables.css" />
    <link rel="stylesheet" href="/css/pages/editor.css" />
    <link rel="stylesheet" href="/css/shared/globals.css" />
    <link rel="stylesheet" href="/css/shared/components.css" />
    <link rel="stylesheet" href="/css/shared/controls.css" />
    <link rel="stylesheet" href="/css/shared/chart.css" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.7/dist/css/bootstrap.min.css" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.13.1/font/bootstrap-icons.min.css" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/gridstack@12.3.3/dist/gridstack.min.css" />
  </head>

  <body>
    <#include "/shared/modal.ftl"> 
    <#include "/pages/editor/components/sidebar.ftl">
    <#include "/pages/editor/components/grid.ftl">

    <div class="dv-layout">
      <@sidebar id=config?eval_json.id!"new-pipeline" />

      <main class="dv-main">
        <@grid />
      </main>

      <@modal />
    </div>

    <script type="module">
      import Editor from "/js/pages/editor/Editor.js";

      const config = JSON.parse("${config?json_string}");
      const editor = new Editor();

      editor.init(config);
    </script>
  </body>
</html>
