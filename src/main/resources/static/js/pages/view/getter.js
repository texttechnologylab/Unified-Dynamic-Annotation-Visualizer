import BarChart from "./widgets/dynamic/BarChart.js";
import LineChart from "./widgets/dynamic/LineChart.js";
import PieChart from "./widgets/dynamic/PieChart.js";
import MedialAxis from "./widgets/dynamic/MedialAxis.js";
import VoronoiDiagram from "./widgets/dynamic/VoronoiDiagram.js";
import HighlightText from "./widgets/dynamic/HighlightText.js";

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
