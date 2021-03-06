package net.demilich.metastone.game.spells.desc.filter;

import net.demilich.metastone.game.Attribute;
import net.demilich.metastone.game.GameContext;
import net.demilich.metastone.game.Player;
import net.demilich.metastone.game.cards.Card;
import net.demilich.metastone.game.cards.CardType;
import net.demilich.metastone.game.cards.Rarity;
import net.demilich.metastone.game.entities.Actor;
import net.demilich.metastone.game.entities.Entity;
import net.demilich.metastone.game.entities.heroes.HeroClass;
import net.demilich.metastone.game.entities.minions.Race;

public class CardFilter extends EntityFilter {

	public CardFilter(FilterDesc desc) {
		super(desc);
	}

	@Override
	protected boolean test(GameContext context, Player player, Entity entity) {
		Card card = null;
		if (entity instanceof Card) {
			card = (Card) entity;
		} else if (entity instanceof Actor) {
			Actor actor = (Actor) entity;
			card = actor.getSourceCard();
		} else {
			return false;
		}

		CardType cardType = (CardType) desc.get(FilterArg.CARD_TYPE);
		if (cardType != null && cardType != card.getCardType()) {
			return false;
		}
		Race race = (Race) desc.get(FilterArg.RACE);
		if (race != null && race != card.getAttribute(Attribute.RACE)) {
			return false;
		}
		
		HeroClass heroClass = (HeroClass) desc.get(FilterArg.HERO_CLASS);
		if (heroClass == HeroClass.OPPONENT) {
			heroClass = context.getOpponent(player).getHero().getHeroClass();
		}
		if (heroClass != null && heroClass != card.getClassRestriction()) {
			return false;
		}
		
		if (desc.contains(FilterArg.MANA_COST)) {
			int manaCost = desc.getValue(FilterArg.MANA_COST, context, player, null, null, 0);
			if (manaCost != card.getBaseManaCost()) {
				return false;
			}
		}
		Rarity rarity = (Rarity) desc.get(FilterArg.RARITY);
		if (rarity != null && card.getRarity() != rarity) {
			return false;
		}

		return true;
	}

}
