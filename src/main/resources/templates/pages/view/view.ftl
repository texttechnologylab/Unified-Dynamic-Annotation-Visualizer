<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${id} - Dynamic Visualizations</title>

    <link rel="stylesheet" href="/css/variables.css" />
    <link rel="stylesheet" href="/css/pages/view.css" />
    <link rel="stylesheet" href="/css/shared/globals.css" />
    <link rel="stylesheet" href="/css/shared/components.css" />
    <link rel="stylesheet" href="/css/shared/controls.css" />
    <link rel="stylesheet" href="/css/shared/chart.css" />
    <link rel="stylesheet" href="/packages/bootstrap-5.3.8/package/dist/css/bootstrap.min.css" />
    <link rel="stylesheet" href="/packages/bootstrap-icons-1.13.1/package/font/bootstrap-icons.min.css" />
    <link rel="stylesheet" href="/packages/gridstack-12.3.3/package/dist/gridstack.min.css" />
  </head>

  <body>
    <#include "/shared/modal.ftl"> 
    <#include "/pages/view/viewSidebar.ftl"> 
    <#include "/pages/view/viewGrid.ftl">

    <div class="dv-layout">
      <@sidebar id=id pipelines=pipelines?eval_json widgets=widgets?eval_json />

      <main class="dv-main">
        <div class="dv-chart-tooltip"></div>

        <@grid />
      </main>

      <@modal />
    </div>

    <script type="module">
      import "/packages/gridstack-12.3.3/package/dist/gridstack-all.js";
      import "/packages/bootstrap-5.3.8/package/dist/js/bootstrap.bundle.min.js";
      import "/packages/d3-7.9.0/package/dist/d3.min.js";
      import View from "/js/pages/view/View.js";

      const widgets = JSON.parse("${widgets?json_string}");
      const view = new View("${id}");
      
      view.init(widgets);
    </script>
  </body>
</html>
