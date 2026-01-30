import BarChart from "./widgets/charts/BarChart.js";
import LineChart from "./widgets/charts/LineChart.js";
import PieChart from "./widgets/charts/PieChart.js";
import MedialAxis from "./widgets/diagrams/MedialAxis.js";
import VoronoiDiagram from "./widgets/diagrams/VoronoiDiagram.js";
import HighlightText from "./widgets/text/HighlightText.js";

import StaticImage from "./widgets/static/StaticImage.js";
import StaticText from "./widgets/static/StaticText.js";
import StaticVideo from "./widgets/static/StaticVideo.js";

const _dynamic = {
  BarChart,
  LineChart,
  PieChart,
  MedialAxis,
  VoronoiDiagram,
  HighlightText,
};

const _static = {
  StaticImage,
  StaticText,
  StaticVideo,
};

export default { _dynamic, _static };
