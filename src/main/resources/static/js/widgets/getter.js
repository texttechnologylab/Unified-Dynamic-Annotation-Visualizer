import BarChart from "./dynamic/BarChart.js";
import LineChart from "./dynamic/LineChart.js";
import PieChart from "./dynamic/PieChart.js";
import MedialAxis from "./dynamic/MedialAxis.js";
import VoronoiDiagram from "./dynamic/VoronoiDiagram.js";
import HighlightText from "./dynamic/HighlightText.js";

import StaticImage from "./static/StaticImage.js";
import StaticText from "./static/StaticText.js";
import StaticVideo from "./static/StaticVideo.js";

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
