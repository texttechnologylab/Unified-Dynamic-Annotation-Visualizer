package org.texttechnologylab.udav.api;

import org.springframework.stereotype.Component;

@Component
public class DummyDataProvider {

    private static final String BAR_CHART_JSON = """
            [
              {
                "label": "NN",
                "value": 140,
                "color": "#ff0000"
              },
              {
                "label": "ART",
                "value": 73,
                "color": "#0000ff"
              },
              {
                "label": "$.",
                "value": 56,
                "color": "#00ff00"
              },
              {
                "label": "$,",
                "value": 46,
                "color": "#ff00ff"
              },
              {
                "label": "APPR",
                "value": 45,
                "color": "#ffc800"
              },
              {
                "label": "PPER",
                "value": 40,
                "color": "#00ffff"
              },
              {
                "label": "ADJA",
                "value": 39,
                "color": "#ffff00"
              },
              {
                "label": "KON",
                "value": 35,
                "color": "#ffafaf"
              },
              {
                "label": "ADV",
                "value": 34,
                "color": "#808080"
              },
              {
                "label": "VVFIN",
                "value": 32,
                "color": "#008080"
              },
              {
                "label": "NE",
                "value": 26,
                "color": "#800080"
              },
              {
                "label": "VAFIN",
                "value": 26,
                "color": "#808000"
              },
              {
                "label": "ADJD",
                "value": 24,
                "color": "#000080"
              },
              {
                "label": "PROAV",
                "value": 14,
                "color": "#ff69b4"
              },
              {
                "label": "VVINF",
                "value": 11,
                "color": "#8b4513"
              },
              {
                "label": "PPOSAT",
                "value": 9,
                "color": "#00ff7f"
              },
              {
                "label": "KOUS",
                "value": 9,
                "color": "#ffa500"
              },
              {
                "label": "PIAT",
                "value": 7,
                "color": "#00bfff"
              },
              {
                "label": "PDAT",
                "value": 6,
                "color": "#9acd32"
              },
              {
                "label": "$(",
                "value": 6,
                "color": "#15c07d"
              },
              {
                "label": "PWAV",
                "value": 6,
                "color": "#63c62a"
              },
              {
                "label": "APPRART",
                "value": 6,
                "color": "#182121"
              },
              {
                "label": "PRELS",
                "value": 5,
                "color": "#d39b55"
              },
              {
                "label": "VMFIN",
                "value": 5,
                "color": "#843652"
              },
              {
                "label": "PTKZU",
                "value": 5,
                "color": "#a2b729"
              },
              {
                "label": "PTKNEG",
                "value": 5,
                "color": "#629977"
              },
              {
                "label": "PTKVZ",
                "value": 4,
                "color": "#3d51de"
              },
              {
                "label": "CARD",
                "value": 4,
                "color": "#b1600b"
              },
              {
                "label": "VVPP",
                "value": 4,
                "color": "#5ae18a"
              },
              {
                "label": "VVIZU",
                "value": 3,
                "color": "#760a06"
              },
              {
                "label": "VAINF",
                "value": 3,
                "color": "#3a87c5"
              },
              {
                "label": "PIS",
                "value": 3,
                "color": "#7c5e64"
              },
              {
                "label": "KOKOM",
                "value": 2,
                "color": "#b24363"
              },
              {
                "label": "PRF",
                "value": 2,
                "color": "#88934b"
              },
              {
                "label": "FM",
                "value": 2,
                "color": "#946969"
              },
              {
                "label": "VMINF",
                "value": 2,
                "color": "#8cf397"
              },
              {
                "label": "PDS",
                "value": 1,
                "color": "#01769f"
              },
              {
                "label": "PTKANT",
                "value": 1,
                "color": "#9e0bcb"
              },
              {
                "label": "APZR",
                "value": 1,
                "color": "#417d62"
              }
            ]
            """;
    private static final String PIE_CHART_JSON = BAR_CHART_JSON;
    private static final String LINE_CHART_JSON = """
            [
              { "name":"Dataset 1","color":"#ff725c","coordinates":[
                  {"y":5,"x":0},{"y":20,"x":20},{"y":10,"x":40},{"y":40,"x":60},{"y":5,"x":80},{"y":60,"x":100}
              ]},
              { "name":"Dataset 2","color":"#a1d99b","coordinates":[
                  {"y":15,"x":-5},{"y":30,"x":15},{"y":20,"x":35},{"y":50,"x":55},{"y":15,"x":75},{"y":70,"x":95}
              ]}
            ]
            """;
    private static final String MAP_2D_JSON = """
            [
              {"type":"LineString","label":"Flight 1","color":"#ff725c","coordinates":[[100,60],[-60,-30]]},
              {"type":"LineString","label":"Flight 2","color":"#ff725c","coordinates":[[10,-20],[-60,-30]]},
              {"type":"LineString","label":"Flight 3","color":"#ff725c","coordinates":[[10,-20],[130,-30]]},
              {"type":"Point","label":"Timbuktu","color":"#ff725c","coordinates":[-3.0026,16.7666]}
            ]
            """;
    private static final String NETWORK_2D_JSON = """
                    {
                      "nodes": [
                        {
                          "id": 1,
                          "name": "A",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 2,
                          "name": "B",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 3,
                          "name": "C",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 4,
                          "name": "D",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 5,
                          "name": "E",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 6,
                          "name": "F",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 7,
                          "name": "G",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 8,
                          "name": "H",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 9,
                          "name": "I",
                          "color": "#69b3a2"
                        },
                        {
                          "id": 10,
                          "name": "J",
                          "color": "#69b3a2"
                        }
                      ],
                      "links": [
                        {
                          "source": 1,
                          "target": 2,
                          "color": "#aaa"
                        },
                        {
                          "source": 1,
                          "target": 5,
                          "color": "#aaa"
                        },
                        {
                          "source": 1,
                          "target": 6,
                          "color": "#aaa"
                        },
                        {
                          "source": 2,
                          "target": 3,
                          "color": "#aaa"
                        },
                        {
                          "source": 2,
                          "target": 7,
                          "color": "#aaa"
                        },
                        {
                          "source": 3,
                          "target": 4,
                          "color": "#aaa"
                        },
                        {
                          "source": 8,
                          "target": 3,
                          "color": "#aaa"
                        },
                        {
                          "source": 4,
                          "target": 5,
                          "color": "#aaa"
                        },
                        {
                          "source": 4,
                          "target": 9,
                          "color": "#aaa"
                        },
                        {
                          "source": 5,
                          "target": 10,
                          "color": "#aaa"
                        }
                      ]
                    }
            """;
    private static final String TEXT_JSON = """
            {
              "text":"Zebras are primarily grazers and can subsist on lower-quality vegetation. They are preyed on mainly by lions, and typically flee when threatened but also bite and kick. Living mostly in savannas and open woodlands, zebras form tight-knit herds that provide protection and increase their chances of spotting predators early. Their striking black-and-white stripes are believed to serve multiple purposes, including camouflage, thermoregulation, and deterring biting insects. Social bonds within the herd are maintained through mutual grooming and coordinated movements during migration.",
              "datasets":[
                {"name":"POS","style":"underline","segments":[
                  {"begin":0,"end":5,"color":"#1f77b4","label":"Noun"},
                  {"begin":11,"end":23,"color":"#2ca02c","label":"Verb"},
                  {"begin":177,"end":185,"color":"#1f77b4","label":"Noun"},
                  {"begin":186,"end":190,"color":"#2ca02c","label":"Verb"},
                  {"begin":343,"end":350,"color":"#1f77b4","label":"Noun"},
                  {"begin":351,"end":359,"color":"#2ca02c","label":"Verb"}]},
                {"name":"Named Entities","style":"bold","segments":[
                  {"begin":26,"end":29,"color":"#ff7f0e","label":"DATE"},
                  {"begin":40,"end":45,"color":"#9467bd","label":"PERSON"},
                  {"begin":197,"end":205,"color":"#ff7f0e","label":"DATE"},
                  {"begin":400,"end":406,"color":"#9467bd","label":"PERSON"}]},
                {"name":"Sentiment","style":"highlight","segments":[
                  {"begin":0,"end":74,"color":"#a1d99b","label":"Positive"},
                  {"begin":74,"end":168,"color":"#fc9272","label":"Negative"},
                  {"begin":169,"end":377,"color":"#a1d99b","label":"Positive"},
                  {"begin":378,"end":513,"color":"#fc9272","label":"Negative"}]}
              ]
            }
            """;

    public String getJsonFor(String id, String type) {
        return switch (id) {
            case "bar-chart" -> BAR_CHART_JSON;
            case "pie-chart" -> PIE_CHART_JSON;
            case "line-chart" -> LINE_CHART_JSON;
            case "map-2d" -> MAP_2D_JSON;
            case "network-2d" -> NETWORK_2D_JSON;
            case "text" -> TEXT_JSON;
            default -> BAR_CHART_JSON; // fallback
        };
    }
}
