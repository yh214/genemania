/**
 * This file is part of GeneMANIA.
 * Copyright (C) 2008-2011 University of Toronto.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.genemania.plugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.genemania.data.normalizer.GeneCompletionProvider2;
import org.genemania.domain.Attribute;
import org.genemania.domain.AttributeGroup;
import org.genemania.domain.Gene;
import org.genemania.domain.GeneData;
import org.genemania.domain.Interaction;
import org.genemania.domain.InteractionNetwork;
import org.genemania.domain.InteractionNetworkGroup;
import org.genemania.domain.NetworkMetadata;
import org.genemania.domain.Node;
import org.genemania.domain.OntologyCategory;
import org.genemania.domain.Organism;
import org.genemania.domain.Tag;
import org.genemania.dto.AttributeDto;
import org.genemania.dto.EnrichmentEngineResponseDto;
import org.genemania.dto.InteractionDto;
import org.genemania.dto.NetworkDto;
import org.genemania.dto.NodeDto;
import org.genemania.dto.OntologyCategoryDto;
import org.genemania.dto.RelatedGenesEngineRequestDto;
import org.genemania.dto.RelatedGenesEngineResponseDto;
import org.genemania.exception.DataStoreException;
import org.genemania.mediator.AttributeMediator;
import org.genemania.mediator.GeneMediator;
import org.genemania.mediator.NodeMediator;
import org.genemania.mediator.OntologyMediator;
import org.genemania.plugin.data.DataSet;
import org.genemania.plugin.data.IMediatorProvider;
import org.genemania.plugin.model.AnnotationEntry;
import org.genemania.plugin.model.Group;
import org.genemania.plugin.model.Network;
import org.genemania.plugin.model.SearchResult;
import org.genemania.plugin.model.SearchResultBuilder;
import org.genemania.plugin.model.impl.SearchResultImpl;
import org.genemania.util.GeneLinkoutGenerator;

/**
 * A collection of utility functions for converting GeneMANIA domain
 * objects to Cytoscape objects.
 */
public class NetworkUtils {
	public NetworkUtils() {
	}
	
	/**
	 * Returns a <code>Map</code> of <code>Gene</code>s for the given organism
	 * corresponding to the given gene names, keyed by the id of the node to
	 * which the <code>Gene</code> belongs. 
	 * @param geneMediator
	 * @param geneNames
	 * @param organism
	 * @return
	 */
	public Map<Long, Gene> createQueryNodes(GeneMediator geneMediator, List<String> geneNames, Organism organism) {
		try {
			List<Gene> genes = geneMediator.getGenes(geneNames, organism.getId());
			Map<Long, Gene> nodes = new HashMap<Long, Gene>();
			for (Gene gene : genes) {
				Node node = gene.getNode();
				nodes.put(node.getId(), gene);
			}
			return nodes;
		} catch (DataStoreException e) {
			return Collections.emptyMap();
		}
	}

	public Map<Long, Collection<Interaction>> createInteractionMap(Map<InteractionNetwork, Collection<Interaction>> sourceInteractions) {
		Map<Long, Collection<Interaction>> networks = new HashMap<Long, Collection<Interaction>>();
		for (Entry<InteractionNetwork, Collection<Interaction>> entry : sourceInteractions.entrySet()) {
			InteractionNetwork network = entry.getKey();
			networks.put(network.getId(), entry.getValue());
		}
		return networks;
	}

	/**
	 * Returns the highest ranking gene symbol for the given <code>Node</code>.
	 */
	public Gene getPreferredGene(Node node) {
		Gene best = null;
		byte bestRank = Byte.MIN_VALUE;
		Collection<Gene> genes = node.getGenes();
		for (Gene gene : genes) {
			byte rank = gene.getNamingSource().getRank();
			if (rank > bestRank) {
				best = gene;
				bestRank = rank;
			}
		}
		return best;
	}

	public double[] sortScores(Map<?, Double> scores) {
		double[] values = new double[scores.size()];
		int i = 0;
		for (Entry<?, Double> entry : scores.entrySet()) {
			values[i] = entry.getValue();
			i++;
		}
		Arrays.sort(values);
		return values;
	}
	
	public <T> List<T> createSortedList(final Map<T,Double> scoredMap) {
		ArrayList<T> list = new ArrayList<T>();
		list.addAll(scoredMap.keySet());
		Collections.sort(list, new Comparator<T>() {
			public int compare(T o1,T o2) {
				double score1 = scoredMap.get(o1);
				double score2 = scoredMap.get(o2);
				return (int) Math.signum(score2 - score1);
			}
		});
		return list;
	}
	
	public Comparator<Group<?, ?>> getNetworkGroupComparator() {
		return new Comparator<Group<?, ?>>() {
			public int compare(Group<?, ?> group1, Group<?, ?> group2) {
				return group1.getName().compareToIgnoreCase(group2.getName());
			}
		};	
	}
	
	public Comparator<Network<?>> getNetworkComparator() {
		return new Comparator<Network<?>>() {
			public int compare(Network<?> network1, Network<?> network2) {
				return network1.getName().compareToIgnoreCase(network2.getName());
			}
		};	
	}
	
	public Color getNetworkColor(DataSet data, Group<?, ?> group) {
		return new Color(data.getColor(group.getCode()).getRgb());
	}
	
	public String buildDescriptionHtml(Network<?> network, Group<?, ?> group) {
		{
			InteractionNetwork adapted = network.adapt(InteractionNetwork.class);
			if (adapted != null) {
				return buildDescriptionHtml(adapted);
			}
		}
		{
			AttributeGroup adapted = network.adapt(AttributeGroup.class);
			if (adapted != null) {
				return buildDescriptionHtml(adapted);
			}
		}
		{
			Attribute adapted = network.adapt(Attribute.class);
			Group<AttributeGroup, Attribute> adaptedGroup = group.adapt(AttributeGroup.class, Attribute.class);
			if (adapted != null && adaptedGroup != null) {
				return buildDescriptionHtml(adapted, adaptedGroup.getModel());
			}
		}
		return ""; //$NON-NLS-1$
	}
	
	private String buildDescriptionHtml(Attribute attribute, AttributeGroup group) {
		StringBuilder builder = new StringBuilder();
		builder.append("<div>"); //$NON-NLS-1$
		builder.append(attribute.getDescription());
		builder.append("</div>"); //$NON-NLS-1$
		
		builder.append(String.format("<div><strong>%s</strong> ", Strings.networkDetailPanelSource_label)); //$NON-NLS-1$
		builder.append(String.format(Strings.networkDetailPanelAttribute_description, group.getDescription(), formatLink(group.getPublicationName(), group.getPublicationUrl())));
		builder.append("</div>"); //$NON-NLS-1$
		
		builder.append(String.format("<div><strong>%s</strong> ", Strings.networkDetailPanelMoreAt_label)); //$NON-NLS-1$
		builder.append(formatLink(group.getLinkoutLabel(), group.getLinkoutUrl()));
		builder.append("</div>"); //$NON-NLS-1$

		return builder.toString();
	}

	private String buildDescriptionHtml(AttributeGroup group) {
		StringBuilder builder = new StringBuilder();
		builder.append("<div>"); //$NON-NLS-1$
		builder.append(String.format(Strings.networkDetailPanelAttribute_description, group.getDescription(), formatLink(group.getPublicationName(), group.getPublicationUrl())));
		builder.append("</div>"); //$NON-NLS-1$
		return builder.toString();
	}

	String buildDescriptionHtml(InteractionNetwork network) {
		StringBuilder builder = new StringBuilder();
		NetworkMetadata data = network.getMetadata();
		
		if (data == null) {
			// No metadata; fallback to whatever
			return network.getDescription();
		}
		
		
		// TODO: Change to match website
		String title = data.getTitle();
		if (!isEmpty(title)) {
			builder.append("<div>"); //$NON-NLS-1$
			builder.append(formatLink(title, data.getUrl()));
			builder.append(". "); //$NON-NLS-1$
			
			String authors = data.getAuthors();
			if (!isEmpty(authors)) {
				builder.append(htmlEscape(formatAuthors(authors)));
				builder.append(". "); //$NON-NLS-1$
			}
			
			String yearPublished = data.getYearPublished();
			if (!isEmpty(yearPublished)) {
				builder.append("("); //$NON-NLS-1$
				builder.append(htmlEscape(yearPublished));
				builder.append("). "); //$NON-NLS-1$
			}
			
			String publication = data.getPublicationName();
			if (!isEmpty(publication)) {
				builder.append(htmlEscape(data.getPublicationName()));
				builder.append("."); //$NON-NLS-1$
			}
			builder.append("</div>"); //$NON-NLS-1$
		}
		
		String other = data.getOther();
		if (!isEmpty(other)) {
			builder.append("<div>"); //$NON-NLS-1$
			builder.append(htmlEscape(other));
			builder.append("</div>"); //$NON-NLS-1$
		}

		String comment = data.getComment();
		if (!isEmpty(comment)) {
			builder.append(String.format("<div><strong>%s</strong> ", Strings.networkDetailPanelComment_label)); //$NON-NLS-1$
			builder.append(htmlEscape(comment));
			builder.append("</div>"); //$NON-NLS-1$
		}

		builder.append(String.format("<div><strong>%s</strong> ", Strings.networkDetailPanelSource_label)); //$NON-NLS-1$
		builder.append(String.format(Strings.networkDetailPanelSource_description, formatProcessingDescription(data.getProcessingDescription()), data.getInteractionCount(), formatLink(data.getSource(), data.getSourceUrl())));
		builder.append("</div>"); //$NON-NLS-1$
		
		Collection<Tag> tags = network.getTags();
		if (tags.size() > 0) {
			builder.append("<div>"); //$NON-NLS-1$
			builder.append(String.format("<strong>%s</strong> ", Strings.networkDetailPanelTags_label)); //$NON-NLS-1$
			int i = 0;
			for (Tag tag : tags) {
				if (i > 0) {
					builder.append(", "); //$NON-NLS-1$
				}
				builder.append(tag.getName().toLowerCase());
				i++;
			}
			builder.append("</div>"); //$NON-NLS-1$
		}
		return builder.toString();
	}

	private String formatProcessingDescription(String processingDescription) {
		return processingDescription;
	}

	private String formatAuthors(String authors) {
		String[] parts = authors.split(","); //$NON-NLS-1$
		if (parts.length == 1) {
			return parts[0];
		}
		
		return parts[0] + ", et al"; //$NON-NLS-1$
	}

	private String formatLink(String title, String url) {
		if (isEmpty(url)) {
			return htmlEscape(title);
		}
		
		return "<a href=\"" + url + "\">" + htmlEscape(title) + "</a>";  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private String htmlEscape(String comment) {
		return comment.replaceAll("&", "&amp;").replaceAll("<", "&lt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	public String buildDescriptionReport(InteractionNetwork network) {
		StringBuilder builder = new StringBuilder();
		NetworkMetadata data = network.getMetadata();
		
		if (data == null) {
			// No metadata; fallback to whatever
			return network.getDescription();
		}
		
		builder.append(String.format(Strings.reportMethod_label, data.getProcessingDescription()));
	
		String comment = data.getComment();
		if (!isEmpty(comment)) {
			if (builder.length() > 0) {
				builder.append("|"); //$NON-NLS-1$
			}
			builder.append(comment);
		}
		
		String authors = data.getAuthors();
		if (!isEmpty(authors)) {
			if (builder.length() > 0) {
				builder.append("|"); //$NON-NLS-1$
			}
			builder.append(String.format(Strings.reportAuthors_label,authors));
		}
		
		String pubMed = data.getPubmedId();
		if (!isEmpty(pubMed)) {
			if (builder.length() > 0) {
				builder.append("|"); //$NON-NLS-1$
			}
			builder.append(String.format(Strings.reportPubMed_label, pubMed));
		}
		
		if (builder.length() > 0) {
			builder.append("|"); //$NON-NLS-1$
		}
		builder.append(String.format(Strings.reportInteraction_label, data.getInteractionCount()));

		String source = data.getSource();
		if (!isEmpty(source)) {
			if (builder.length() > 0) {
				builder.append("|"); //$NON-NLS-1$
			}
			builder.append(String.format(Strings.reportSource_label, source));
		}
		
		Collection<Tag> tags = network.getTags();
		if (tags.size() > 0) {
			if (builder.length() > 0) {
				builder.append("|"); //$NON-NLS-1$
			}
			builder.append(Strings.reportTags_label);
			int i = 0;
			for (Tag tag : tags) {
				if (i > 0) {
					builder.append(","); //$NON-NLS-1$
				}
				builder.append(tag.getName());
				i++;
			}
		}
		return builder.toString();
	}
	
	private boolean isEmpty(String string) {
		return string == null || string.length() == 0;
	}

	public String getGeneLabel(Gene gene) {
		Gene preferredGene = getPreferredGene(gene.getNode());
		if (preferredGene.getId() == gene.getId()) {
			return gene.getSymbol();
		}
		return String.format("%s (%s)", preferredGene.getSymbol(), gene.getSymbol()); //$NON-NLS-1$
	}

	public Collection<Interaction> computeCombinedInteractions(Map<InteractionNetwork, Collection<Interaction>> source) {
		List<Interaction> interactions = new ArrayList<Interaction>();
		Map<Long, Set<Long>> seenNodes = new HashMap<Long, Set<Long>>();
		
		for (Collection<Interaction> network : source.values()) {
			for (Interaction interaction : network) {
				long fromId = interaction.getFromNode().getId();
				long toId = interaction.getToNode().getId();
				
				// Canonicalize our lookup data by ensuring the fromId is
				// smaller than the toId.
				if (fromId > toId) {
					fromId = toId;
					toId = interaction.getFromNode().getId();
				}
				
				Set<Long> toIds = seenNodes.get(fromId);
				if (toIds == null) {
					toIds = new HashSet<Long>();
					seenNodes.put(fromId, toIds);
					toIds.add(toId);
					interactions.add(interaction);
				} else {
					if (toIds.contains(toId)) {
						continue;
					}
					toIds.add(toId);
					interactions.add(interaction);
				}
			}
		}
		return interactions;
	}

	public void computeSourceInteractions(List<NetworkDto> networks, Map<Long, InteractionNetwork> canonicalNetworks, Organism organism, DataSet data) {
		IMediatorProvider mediatorProvider = data.getMediatorProvider();
		NodeMediator nodeMediator = mediatorProvider.getNodeMediator();
		
		for (NetworkDto networkVo : networks) {
			InteractionNetwork network = canonicalNetworks.get(networkVo.getId());
			if (network == null) {
				continue;
			}
			List<Interaction> interactions = new ArrayList<Interaction>();
			for (InteractionDto interactionVo : networkVo.getInteractions()) {
				Node fromNode = nodeMediator.getNode(interactionVo.getNodeVO1().getId(), organism.getId());
				Node toNode = nodeMediator.getNode(interactionVo.getNodeVO2().getId(), organism.getId());
				Interaction interaction = new Interaction(fromNode, toNode, (float) interactionVo.getWeight(), null);
				interactions.add(interaction);
			}
			network.setInteractions(interactions);
		}
	}

	public Map<Gene, Double> computeGeneScores(List<NodeDto> nodes, Map<Long, Gene> queryGenes, Organism organism, NodeMediator nodeMediator) {
		// Figure out what the unique set of nodes is so we don't end up
		// creating model objects unnecessarily.
		Map<Long, NodeDto> uniqueNodes = new HashMap<Long, NodeDto>();
		for (NodeDto nodeDto : nodes) {
			uniqueNodes.put(nodeDto.getId(), nodeDto);
		}
		
		double maxScore = 0;
		Map<Gene, Double> scores = new HashMap<Gene, Double>();
		for (Entry<Long, NodeDto> entry : uniqueNodes.entrySet()) {
			long nodeId = entry.getKey();
			Gene gene = queryGenes.get(nodeId);
			if (gene == null) {
				Node node = nodeMediator.getNode(nodeId, organism.getId());
				gene = getPreferredGene(node);
			}
			if (gene == null) {
				continue;
			}
			double score = entry.getValue().getScore();
			maxScore = Math.max(maxScore, score);
			scores.put(gene, score);
		}
		
		for (Gene gene : queryGenes.values()) {
			if (!scores.containsKey(gene)) {
				scores.put(gene, maxScore);
			}
		}
		
		return scores;
	}

	public Map<InteractionNetwork, Double> computeNetworkWeights(List<NetworkDto> networks, Map<Long, InteractionNetwork> canonicalNetworks, Map<Attribute, Double> attributeWeights) {
		double totalAttributeWeight = 0;
		if (attributeWeights != null) {
			for (Double weight : attributeWeights.values()) {
				totalAttributeWeight += weight;
			}
		}
		double scaleFactor = 1 - totalAttributeWeight;
		
		Map<InteractionNetwork, Double> networkWeights = new HashMap<InteractionNetwork, Double>();
		for (NetworkDto networkVo : networks) {
			InteractionNetwork network = canonicalNetworks.get(networkVo.getId());
			if (network == null) {
				network = new InteractionNetwork();
				network.setId(networkVo.getId());
			}
			networkWeights.put(network, networkVo.getWeight() * scaleFactor);
		}
		return networkWeights;
	}

	public Map<Long, Gene> computeQueryGenes(List<String> geneSymbols, GeneCompletionProvider2 geneProvider) {
		Map<Long, Gene> genesByNodeId = new HashMap<Long, Gene>();
		for (String symbol : geneSymbols) {
			Gene gene = geneProvider.getGene(symbol);
			if (gene == null) {
				continue;
			}
			genesByNodeId.put(gene.getNode().getId(), gene);
		}
		return genesByNodeId;
	}

	public Map<Long, InteractionNetworkGroup> computeGroupsByNetwork(RelatedGenesEngineResponseDto response, DataSet data) {
		Map<Long, InteractionNetworkGroup> groups = new HashMap<Long, InteractionNetworkGroup>();
		Map<Long, InteractionNetworkGroup> groupsByNetwork = new HashMap<Long, InteractionNetworkGroup>();
		List<NetworkDto> networks = response.getNetworks();
		for (NetworkDto network : networks) {
			long networkId = network.getId();
			
			InteractionNetworkGroup group = data.getNetworkGroup(networkId);
			if (group == null) {
				continue;
			}
			
			InteractionNetworkGroup canonicalGroup = groups.get(group.getId());
			if (canonicalGroup == null) {
				groups.put(group.getId(), group);
				canonicalGroup = group;
			}
			groupsByNetwork.put(networkId, canonicalGroup);
		}
		return groupsByNetwork;
	}
	
	public SearchResult createSearchOptions(Organism organism, RelatedGenesEngineRequestDto request, RelatedGenesEngineResponseDto response, EnrichmentEngineResponseDto enrichmentResponse, DataSet data, List<String> genes) {
		SearchResultBuilder config = new SearchResultImpl();
		
		config.setOrganism(organism);
		
		GeneCompletionProvider2 geneProvider = data.getCompletionProvider(organism);
		Map<Long, Gene> queryGenes = computeQueryGenes(genes, geneProvider);
		config.setSearchQuery(queryGenes);
		
		config.setCombiningMethod(request.getCombiningMethod());
		config.setGeneSearchLimit(request.getLimitResults());
		config.setAttributeSearchLimit(request.getAttributesLimit());
		
		Map<Long, InteractionNetworkGroup> groupsByNetwork = computeGroupsByNetwork(response, data);
		config.setGroups(groupsByNetwork);

		IMediatorProvider provider = data.getMediatorProvider();
		NodeMediator nodeMediator = provider.getNodeMediator();
		List<NetworkDto> sourceNetworks = response.getNetworks();
		config.setGeneScores(computeGeneScores(response.getNodes(), queryGenes, organism, nodeMediator));
		
		Map<Long, InteractionNetwork> canonicalNetworks = computeCanonicalNetworks(groupsByNetwork);
		computeSourceInteractions(sourceNetworks, canonicalNetworks, organism, data);
		
		AttributeMediator attributeMediator = provider.getAttributeMediator();
		computeAttributes(config, organism, response.getAttributes(), response.getNodeToAttributes(), attributeMediator);
		
		config.setNetworkWeights(computeNetworkWeights(sourceNetworks, canonicalNetworks, config.getAttributeWeights()));
		
		if (enrichmentResponse != null) {
			config.setEnrichment(processAnnotations(enrichmentResponse.getAnnotations(), data));
		}
		return config.build();
	}

	private Map<Long, InteractionNetwork> computeCanonicalNetworks(Map<Long, InteractionNetworkGroup> groupsByNetwork) {
		Map<Long, InteractionNetwork> canonicalNetworks = new HashMap<Long, InteractionNetwork>();
		for (InteractionNetworkGroup group : groupsByNetwork.values()) {
			for (InteractionNetwork network : group.getInteractionNetworks()) {
				canonicalNetworks.put(network.getId(), network);
			}
		}
		return canonicalNetworks;
	}

	private void computeAttributes(SearchResultBuilder config, Organism organism, Collection<AttributeDto> source, Map<Long, Collection<AttributeDto>> nodeToAttributes, AttributeMediator mediator) {
		if (source == null || nodeToAttributes == null) {
			return;
		}
		
		Map<Long, Attribute> attributes = new HashMap<Long, Attribute>();
		Map<Long, AttributeGroup> groupsByAttribute = new HashMap<Long, AttributeGroup>();
		Map<Long, AttributeGroup> groups = new HashMap<Long, AttributeGroup>();
		Map<Long, Collection<Attribute>> attributesByNode = new HashMap<Long, Collection<Attribute>>();
		Map<Attribute, Double> weights = new HashMap<Attribute, Double>();
		
		long organismId = organism.getId();
		for (AttributeDto item : source) {
			Attribute attribute = mediator.findAttribute(organismId, item.getId());
			attributes.put(attribute.getId(), attribute);
			
			AttributeGroup group = groups.get(item.getGroupId());
			if (group == null) {
				group = mediator.findAttributeGroup(organismId, item.getGroupId()); 
			}
				
			groupsByAttribute.put(item.getId(), group);
			groups.put(item.getGroupId(), group);
			weights.put(attribute, item.getWeight());
		}

		for (Entry<Long, Collection<AttributeDto>> entry : nodeToAttributes.entrySet()) {
			Collection<AttributeDto> sourceAttributes = entry.getValue();
			Collection<Attribute> nodeAttributes = new ArrayList<Attribute>(sourceAttributes.size());
			for (AttributeDto item : sourceAttributes) {
				Attribute attribute = attributes.get(item.getId());
				nodeAttributes.add(attribute);
			}
			attributesByNode.put(entry.getKey(), nodeAttributes);
		}
		config.setAttributes(attributesByNode);
		config.setAttributeWeights(weights);
		config.setGroupsByAttribute(groupsByAttribute);
	}

	private Map<Long, Collection<AnnotationEntry>> processAnnotations(Map<Long, Collection<OntologyCategoryDto>> annotations, DataSet data) {
		OntologyMediator mediator = data.getMediatorProvider().getOntologyMediator();
		Map<Long, Collection<AnnotationEntry>> result = new HashMap<Long, Collection<AnnotationEntry>>();
		Map<Long, AnnotationEntry> annotationCache = new HashMap<Long, AnnotationEntry>();
		
		for (Entry<Long, Collection<OntologyCategoryDto>> entry : annotations.entrySet()) {
			long nodeId = entry.getKey();
			Set<AnnotationEntry> nodeAnnotations = new HashSet<AnnotationEntry>();
			
			for (OntologyCategoryDto categoryVo : entry.getValue()) {
				long categoryId = categoryVo.getId();
				AnnotationEntry annotation = annotationCache.get(categoryId);
				if (annotation == null) {
					try {
						OntologyCategory category = mediator.getCategory(categoryId);
						annotation = new AnnotationEntry(category, categoryVo);
						annotationCache.put(categoryId, annotation);
					} catch (DataStoreException e) {
						Logger logger = Logger.getLogger(NetworkUtils.class);
						logger.error(String.format("Can't find category: %d", categoryId), e); //$NON-NLS-1$
						continue;
					}
				}
				nodeAnnotations.add(annotation);
			}
			if (nodeAnnotations.size() > 0) {
				result.put(nodeId, nodeAnnotations);
			}
		}
		return result;
	}

	public void normalizeNetworkWeights(RelatedGenesEngineResponseDto result) {
		double totalWeight = 0;
		for (NetworkDto network : result.getNetworks()) {
			totalWeight += network.getWeight();
		}
		
		if (totalWeight == 0) {
			return;
		}

		double correctionFactor = 1 / totalWeight;
		for (NetworkDto network : result.getNetworks()) {
			network.setWeight(network.getWeight() * correctionFactor);
		}
	}

	public String buildGeneDescription(Gene gene) {
		Node node = gene.getNode();
		GeneData data = node.getGeneData();
		
		boolean first = true;
		StringBuilder builder = new StringBuilder();
		Map<String, String> linkouts = GeneLinkoutGenerator.instance().getLinkouts(gene.getOrganism(), node);
		for (Entry<String, String> entry : linkouts.entrySet()) {
			if (!first) {
				builder.append(", "); //$NON-NLS-1$
			}
			builder.append(String.format("<a href=\"%s\">%s</a>", htmlEscape(entry.getValue()), entry.getKey())); //$NON-NLS-1$
			first = false;
		}
		if (builder.length() == 0) {
			return String.format(Strings.geneDetailPanelDescription_label, htmlEscape(data.getDescription()), builder.toString());
		}
		return String.format(Strings.geneDetailPanelDescription2_label, htmlEscape(data.getDescription()), builder.toString());
	}
}
