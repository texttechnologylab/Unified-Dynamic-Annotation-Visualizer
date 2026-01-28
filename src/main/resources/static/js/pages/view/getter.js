import BarChart from "./widgets/charts/BarChart.js";
import LineChart from "./widgets/charts/LineChart.js";
import PieChart from "./widgets/charts/PieChart.js";
import MedialAxis from "./widgets/diagrams/MedialAxis.js";
import VoronoiDiagram2D from "./widgets/diagrams/VoronoiDiagram2D.js";
import HighlightText from "./widgets/text/HighlightText.js";
import Map2D from "./widgets/maps/Map2D.js";
import Network2D from "./widgets/networks/Network2D.js";
import ScrollableTable from "./widgets/tables/ScrollableTable.js";

import StaticIFrame from "./widgets/static/StaticIFrame.js";
import StaticImage from "./widgets/static/StaticImage.js";
import StaticText from "./widgets/static/StaticText.js";
import StaticVideo from "./widgets/static/StaticVideo.js";

const _dynamic = {
  BarChart,
  LineChart,
  PieChart,
  MedialAxis,
  VoronoiDiagram2D,
  HighlightText,
  Map2D,
  Network2D,
  ScrollableTable,
};

const _static = {
  StaticIFrame,
  StaticImage,
  StaticText,
  StaticVideo,
};

export default { _dynamic, _static };
